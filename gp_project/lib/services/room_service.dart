import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/room_model.dart';

class RoomService {
  final ApiClient _apiClient;
  RoomService(this._apiClient);

  Future<List<RoomModel>> getRooms(int workspaceId, {int page = 1, int limit = 20}) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.roomServiceBase}/api/rooms?workspaceId=$workspaceId&page=$page&limit=$limit');
    final response = await http.get(uri, headers: headers);
    dev.log('[ROOM] getRooms → ${response.statusCode}', name: 'RoomService');
    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final items = body is List ? body : (body['items'] ?? body['content'] ?? body['data'] ?? []);
      return (items as List).map((e) => RoomModel.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception('Failed to load rooms (${response.statusCode})');
  }

  Future<RoomModel> getRoom(String id) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$id');
    final response = await http.get(uri, headers: headers);
    if (response.statusCode == 200) {
      return RoomModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    throw Exception('Failed to load room (${response.statusCode})');
  }

  Future<RoomModel> createRoom({
    required String name,
    required int workspaceId,
    List<int> members = const [],
    int? maxParticipants,
  }) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms');
    final body = <String, dynamic>{'name': name, 'workspaceId': workspaceId};
    if (members.isNotEmpty) body['members'] = members;
    if (maxParticipants != null) body['maxParticipants'] = maxParticipants;
    final response = await http.post(uri, headers: headers, body: jsonEncode(body));
    dev.log('[ROOM] createRoom → ${response.statusCode}', name: 'RoomService');
    if (response.statusCode == 200 || response.statusCode == 201) {
      return RoomModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    _throwError(response, 'Failed to create room');
  }

  Future<RoomModel> updateRoom(String id, {String? name, int? maxParticipants}) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$id');
    final body = <String, dynamic>{};
    if (name != null) body['name'] = name;
    if (maxParticipants != null) body['maxParticipants'] = maxParticipants;
    final response = await http.patch(uri, headers: headers, body: jsonEncode(body));
    if (response.statusCode == 200) {
      return RoomModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    _throwError(response, 'Failed to update room');
  }

  Future<void> deleteRoom(String id) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$id');
    final response = await http.delete(uri, headers: headers);
    if (response.statusCode != 200 && response.statusCode != 204) {
      _throwError(response, 'Failed to delete room');
    }
  }

  Future<void> addMember(String roomId, int userId) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$roomId/members');
    final response = await http.post(uri, headers: headers, body: jsonEncode({'userId': userId}));
    if (response.statusCode != 200 && response.statusCode != 204) {
      _throwError(response, 'Failed to add member');
    }
  }

  Future<void> removeMember(String roomId, int userId) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$roomId/members/$userId');
    final response = await http.delete(uri, headers: headers);
    if (response.statusCode != 200 && response.statusCode != 204) {
      _throwError(response, 'Failed to remove member');
    }
  }

  Future<JoinRoomResponse> joinRoom(String id) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$id/join');
    final response = await http.post(uri, headers: headers);
    dev.log('[ROOM] joinRoom → ${response.statusCode}', name: 'RoomService');
    if (response.statusCode == 200) {
      return JoinRoomResponse.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    _throwError(response, 'Failed to join room');
  }

  Future<void> leaveRoom(String id) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$id/leave');
    final response = await http.post(uri, headers: headers);
    if (response.statusCode != 200 && response.statusCode != 204) {
      _throwError(response, 'Failed to leave room');
    }
  }

  Future<List<ParticipantModel>> getParticipants(String id) async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/$id/participants');
    final response = await http.get(uri, headers: headers);
    if (response.statusCode == 200) {
      return (jsonDecode(response.body) as List)
          .map((e) => ParticipantModel.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  Future<String> getWsTicket() async {
    final headers = await _apiClient.roomServiceHeaders();
    final uri = Uri.parse('${ApiConstants.roomServiceBase}/api/rooms/ws-ticket');
    final response = await http.post(uri, headers: headers);
    if (response.statusCode == 200 || response.statusCode == 201) {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      return body['ticket'] as String;
    }
    throw Exception('Failed to get room WS ticket (${response.statusCode})');
  }

  Never _throwError(http.Response r, String fallback) {
    try {
      final b = jsonDecode(r.body) as Map<String, dynamic>;
      throw Exception(b['message'] ?? b['error'] ?? '$fallback (${r.statusCode})');
    } catch (_) {
      throw Exception('$fallback (${r.statusCode})');
    }
  }
}
