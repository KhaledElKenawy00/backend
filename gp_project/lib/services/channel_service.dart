import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/channel_model.dart';

class ChannelService {
  final ApiClient _apiClient;

  ChannelService(this._apiClient);

  Future<List<ChannelModel>> getChannels({
    required int workspaceId,
    int page = 1,
    int limit = 20,
  }) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
      '${ApiConstants.chatServiceBase}/api/chat/channels?workspaceId=$workspaceId&page=$page&limit=$limit',
    );
    dev.log('[CHANNEL] GET $uri', name: 'ChannelService');
    final response = await http.get(uri, headers: headers);
    dev.log('[CHANNEL] getChannels → ${response.statusCode}  body=${response.body}', name: 'ChannelService');

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      List<dynamic> items;
      if (body is List) {
        items = body;
      } else if (body is Map) {
        items = body['items'] as List? ??
            body['content'] as List? ??
            body['data'] as List? ??
            [];
      } else {
        items = [];
      }
      dev.log('[CHANNEL] getChannels parsed ${items.length} channels', name: 'ChannelService');
      return items
          .map((e) => ChannelModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } else {
      dev.log('[CHANNEL] getChannels FAILED ${response.statusCode}: ${response.body}', name: 'ChannelService');
      throw Exception('Failed to load channels (${response.statusCode})');
    }
  }

  Future<ChannelModel> getChannel(String channelId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId');
    dev.log('[CHANNEL] GET $uri', name: 'ChannelService');
    final response = await http.get(uri, headers: headers);
    dev.log('[CHANNEL] getChannel → ${response.statusCode}  body=${response.body}', name: 'ChannelService');

    if (response.statusCode == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return ChannelModel.fromJson(json);
    } else {
      throw Exception('Failed to load channel (${response.statusCode})');
    }
  }

  Future<ChannelModel> createChannel({
    required String name,
    required int workspaceId,
    List<int> members = const [],
  }) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri =
        Uri.parse('${ApiConstants.chatServiceBase}/api/chat/channels');
    final requestBody = jsonEncode({'name': name, 'workspaceId': workspaceId, 'members': members});
    dev.log('[CHANNEL] POST $uri  body=$requestBody', name: 'ChannelService');
    final response = await http.post(uri, headers: headers, body: requestBody);
    dev.log('[CHANNEL] createChannel → ${response.statusCode}  body=${response.body}', name: 'ChannelService');

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return ChannelModel.fromJson(json);
    } else {
      String errorMsg = 'Failed to create channel (${response.statusCode})';
      try {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        errorMsg = json['message'] as String? ?? errorMsg;
      } catch (_) {}
      dev.log('[CHANNEL] createChannel FAILED: $errorMsg', name: 'ChannelService');
      throw Exception(errorMsg);
    }
  }

  Future<void> joinChannel(String channelId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/join');
    dev.log('[CHANNEL] POST $uri (join)', name: 'ChannelService');
    final response = await http.post(uri, headers: headers);
    dev.log('[CHANNEL] joinChannel → ${response.statusCode}', name: 'ChannelService');

    if (response.statusCode != 200 && response.statusCode != 204) {
      throw Exception('Failed to join channel (${response.statusCode})');
    }
  }

  Future<void> leaveChannel(String channelId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/leave');
    dev.log('[CHANNEL] POST $uri (leave)', name: 'ChannelService');
    final response = await http.post(uri, headers: headers);
    dev.log('[CHANNEL] leaveChannel → ${response.statusCode}', name: 'ChannelService');

    if (response.statusCode != 200 && response.statusCode != 204) {
      throw Exception('Failed to leave channel (${response.statusCode})');
    }
  }

  Future<ChannelModel> createOrGetDm(int targetUserId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/dm');
    final requestBody = jsonEncode({'targetUserId': targetUserId});
    dev.log('[CHANNEL] POST $uri (createDM)  body=$requestBody', name: 'ChannelService');
    final response = await http.post(uri, headers: headers, body: requestBody);
    dev.log('[CHANNEL] createOrGetDm → ${response.statusCode}  body=${response.body}', name: 'ChannelService');

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return ChannelModel.fromJson(json);
    } else {
      dev.log('[CHANNEL] createOrGetDm FAILED: ${response.body}', name: 'ChannelService');
      throw Exception('Failed to create DM (${response.statusCode})');
    }
  }

  Future<List<ChannelModel>> getDms({int page = 1, int limit = 20}) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/dm?page=$page&limit=$limit');
    dev.log('[CHANNEL] GET $uri (getDMs)', name: 'ChannelService');
    final response = await http.get(uri, headers: headers);
    dev.log('[CHANNEL] getDms → ${response.statusCode}  body=${response.body}', name: 'ChannelService');

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      List<dynamic> items;
      if (body is List) {
        items = body;
      } else if (body is Map) {
        items = body['items'] as List? ??
            body['content'] as List? ??
            body['data'] as List? ??
            [];
      } else {
        items = [];
      }
      dev.log('[CHANNEL] getDms parsed ${items.length} DMs', name: 'ChannelService');
      return items
          .map((e) => ChannelModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } else {
      dev.log('[CHANNEL] getDms FAILED: ${response.body}', name: 'ChannelService');
      throw Exception('Failed to load DMs (${response.statusCode})');
    }
  }

  Future<void> markChannelRead(String channelId, String lastReadMessageId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/read');
    dev.log('[CHANNEL] POST $uri  lastReadMessageId=$lastReadMessageId', name: 'ChannelService');
    final response = await http.post(
      uri,
      headers: headers,
      body: jsonEncode({'lastReadMessageId': lastReadMessageId}),
    );
    dev.log('[CHANNEL] markChannelRead → ${response.statusCode}', name: 'ChannelService');
  }

  Future<int> getUnreadCount(String channelId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/unread');
    final response = await http.get(uri, headers: headers);
    dev.log('[CHANNEL] getUnreadCount($channelId) → ${response.statusCode}  body=${response.body}', name: 'ChannelService');

    if (response.statusCode == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return json['unreadCount'] as int? ?? 0;
    }
    return 0;
  }
}
