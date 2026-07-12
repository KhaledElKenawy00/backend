import 'dart:async';
import 'dart:developer' as dev;
import 'package:flutter/foundation.dart';
import '../core/network/api_client.dart';
import '../models/room_model.dart';
import '../services/room_service.dart';
import '../services/room_ws_service.dart';

class RoomProvider extends ChangeNotifier {
  final ApiClient _apiClient;
  late final RoomService _roomService;
  late final RoomWsService _wsService;

  List<RoomModel> _rooms = [];
  RoomModel? _activeRoom;
  List<ParticipantModel> _participants = [];
  String? _agoraChannelName;
  String? _agoraToken;
  bool _isLoading = false;
  bool _isInRoom = false;
  String? _error;
  Timer? _heartbeatTimer;

  RoomProvider(this._apiClient) {
    _roomService = RoomService(_apiClient);
    _wsService = RoomWsService(_apiClient);
  }

  List<RoomModel> get rooms => List.unmodifiable(_rooms);
  RoomModel? get activeRoom => _activeRoom;
  List<ParticipantModel> get participants => List.unmodifiable(_participants);
  String? get agoraChannelName => _agoraChannelName;
  String? get agoraToken => _agoraToken;
  bool get isLoading => _isLoading;
  bool get isInRoom => _isInRoom;
  String? get error => _error;

  Future<void> loadRooms(int workspaceId, {bool silent = false}) async {
    if (!silent) {
      _isLoading = true;
      _error = null;
      notifyListeners();
    }
    try {
      _rooms = await _roomService.getRooms(workspaceId);
      dev.log('[ROOM_PROVIDER] loaded ${_rooms.length} rooms', name: 'RoomProvider');
    } catch (e) {
      if (!silent) _error = e.toString().replaceFirst('Exception: ', '');
      dev.log('[ROOM_PROVIDER] loadRooms error: $e', name: 'RoomProvider');
    } finally {
      if (!silent) _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> initWebSocket() async {
    await _wsService.init();
  }

  Future<void> joinRoom(String roomId) async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _roomService.joinRoom(roomId);
      _activeRoom = result.room;
      _agoraChannelName = result.agoraChannelName;
      _agoraToken = result.agoraToken.isEmpty ? null : result.agoraToken;
      _participants = result.participants;
      _isInRoom = true;

      _wsService.subscribeToRoom(roomId, _handleRoomEvent);
      _startHeartbeat(roomId);

      dev.log('[ROOM_PROVIDER] joined room $roomId — agoraChannel=$_agoraChannelName', name: 'RoomProvider');
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
      dev.log('[ROOM_PROVIDER] joinRoom error: $e', name: 'RoomProvider');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> leaveRoom() async {
    final roomId = _activeRoom?.id;
    if (roomId == null) return;
    _stopHeartbeat();
    _wsService.unsubscribeFromRoom();
    try {
      await _roomService.leaveRoom(roomId);
    } catch (_) {}
    _activeRoom = null;
    _participants = [];
    _agoraChannelName = null;
    _agoraToken = null;
    _isInRoom = false;
    notifyListeners();
  }

  Future<RoomModel> createRoom({
    required String name,
    required int workspaceId,
    List<int> members = const [],
    int? maxParticipants,
  }) async {
    final room = await _roomService.createRoom(
      name: name,
      workspaceId: workspaceId,
      members: members,
      maxParticipants: maxParticipants,
    );
    _rooms = [room, ..._rooms];
    notifyListeners();
    return room;
  }

  Future<void> updateRoom(String id, {String? name, int? maxParticipants}) async {
    final updated = await _roomService.updateRoom(id, name: name, maxParticipants: maxParticipants);
    _rooms = _rooms.map((r) => r.id == id ? updated : r).toList();
    notifyListeners();
  }

  Future<void> addMember(String roomId, int userId) async {
    await _roomService.addMember(roomId, userId);
    _rooms = _rooms.map((r) {
      if (r.id != roomId) return r;
      if (r.members.contains(userId)) return r;
      return RoomModel(
        id: r.id, name: r.name, workspaceId: r.workspaceId,
        createdBy: r.createdBy, members: [...r.members, userId],
        agoraChannelName: r.agoraChannelName,
        maxParticipants: r.maxParticipants,
      );
    }).toList();
    notifyListeners();
  }

  Future<void> removeMember(String roomId, int userId) async {
    await _roomService.removeMember(roomId, userId);
    _rooms = _rooms.map((r) {
      if (r.id != roomId) return r;
      return RoomModel(
        id: r.id, name: r.name, workspaceId: r.workspaceId,
        createdBy: r.createdBy, members: r.members.where((m) => m != userId).toList(),
        agoraChannelName: r.agoraChannelName,
        maxParticipants: r.maxParticipants,
      );
    }).toList();
    notifyListeners();
  }

  Future<void> deleteRoom(String id) async {
    await _roomService.deleteRoom(id);
    _rooms = _rooms.where((r) => r.id != id).toList();
    notifyListeners();
  }

  void sendStateUpdate({required bool muted, required bool cameraOn, required bool screenSharing}) {
    _wsService.sendStateUpdate(muted: muted, cameraOn: cameraOn, screenSharing: screenSharing);
  }

  void _handleRoomEvent(String action, Map<String, dynamic> payload) {
    dev.log('[ROOM_PROVIDER] event action=$action', name: 'RoomProvider');
    switch (action) {
      case 'PARTICIPANT_JOINED':
        final p = ParticipantModel.fromJson(payload);
        if (!_participants.any((x) => x.userId == p.userId)) {
          _participants = [..._participants, p];
        }
      case 'PARTICIPANT_LEFT':
        final userId = payload['userId'] as int?;
        if (userId != null) {
          _participants = _participants.where((p) => p.userId != userId).toList();
        }
      case 'STATE_CHANGED':
        final userId = payload['userId'] as int?;
        if (userId != null) {
          _participants = _participants.map((p) {
            if (p.userId != userId) return p;
            return p.copyWith(
              muted: payload['muted'] as bool?,
              cameraOn: payload['cameraOn'] as bool?,
              screenSharing: payload['screenSharing'] as bool?,
            );
          }).toList();
        }
      case 'ROOM_UPDATED':
        if (_activeRoom != null) {
          _activeRoom = RoomModel.fromJson(payload);
        }
      case 'ROOM_CLOSED':
        _isInRoom = false;
        _activeRoom = null;
        _participants = [];
    }
    notifyListeners();
  }

  void _startHeartbeat(String roomId) {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(const Duration(seconds: 15), (_) {
      _wsService.sendHeartbeat(roomId);
    });
  }

  void _stopHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _stopHeartbeat();
    _wsService.dispose();
    super.dispose();
  }
}
