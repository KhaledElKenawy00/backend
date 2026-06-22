import 'dart:convert';
import 'dart:developer' as dev;
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../core/constants/api_constants.dart';
import 'room_service.dart';
import '../core/network/api_client.dart';

typedef RoomEventCallback = void Function(String action, Map<String, dynamic> payload);

class RoomWsService {
  final ApiClient _apiClient;
  late final RoomService _roomService;
  StompClient? _stompClient;
  bool _isConnected = false;
  String? _activeRoomId;
  StompUnsubscribe? _roomSub;

  RoomWsService(this._apiClient) {
    _roomService = RoomService(_apiClient);
  }

  bool get isConnected => _isConnected;

  Future<void> init() async {
    if (_isConnected) return;
    try {
      final ticket = await _roomService.getWsTicket();
      final wsUrl = '${ApiConstants.roomWsUrl}?ticket=$ticket';
      dev.log('[ROOM_WS] connecting to $wsUrl', name: 'RoomWsService');

      _stompClient = StompClient(
        config: StompConfig(
          url: wsUrl,
          onConnect: (frame) {
            _isConnected = true;
            dev.log('[ROOM_WS] STOMP connected', name: 'RoomWsService');
          },
          onDisconnect: (_) {
            _isConnected = false;
            dev.log('[ROOM_WS] STOMP disconnected', name: 'RoomWsService');
          },
          onWebSocketError: (e) {
            _isConnected = false;
            dev.log('[ROOM_WS] WS error: $e', name: 'RoomWsService');
          },
          reconnectDelay: Duration.zero,
        ),
      );
      _stompClient!.activate();
    } catch (e) {
      dev.log('[ROOM_WS] init error: $e', name: 'RoomWsService');
    }
  }

  void subscribeToRoom(String roomId, RoomEventCallback onEvent) {
    if (_stompClient == null || !_isConnected) return;
    _activeRoomId = roomId;
    _roomSub = _stompClient!.subscribe(
      destination: '/topic/room/$roomId',
      callback: (frame) {
        final body = frame.body;
        if (body == null || body.isEmpty) return;
        try {
          final json = jsonDecode(body) as Map<String, dynamic>;
          final action = json['action'] as String? ?? '';
          final payload = json['payload'] as Map<String, dynamic>? ?? {};
          dev.log('[ROOM_WS] event action=$action', name: 'RoomWsService');
          onEvent(action, payload);
        } catch (e) {
          dev.log('[ROOM_WS] parse error: $e', name: 'RoomWsService');
        }
      },
    );
  }

  void unsubscribeFromRoom() {
    _roomSub?.call();
    _roomSub = null;
    _activeRoomId = null;
  }

  void sendStateUpdate({required bool muted, required bool cameraOn, required bool screenSharing}) {
    if (_stompClient == null || !_isConnected || _activeRoomId == null) return;
    _stompClient!.send(
      destination: '/app/room/state',
      body: jsonEncode({
        'roomId': _activeRoomId,
        'muted': muted,
        'cameraOn': cameraOn,
        'screenSharing': screenSharing,
      }),
    );
  }

  void sendHeartbeat(String roomId) {
    if (_stompClient == null || !_isConnected) return;
    _stompClient!.send(
      destination: '/app/room/heartbeat',
      body: jsonEncode({'roomId': roomId}),
    );
  }

  void dispose() {
    unsubscribeFromRoom();
    _stompClient?.deactivate();
    _stompClient = null;
    _isConnected = false;
  }
}
