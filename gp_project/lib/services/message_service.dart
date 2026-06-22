import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/message_model.dart';

class MessageService {
  final ApiClient _apiClient;

  MessageService(this._apiClient);

  Future<List<MessageModel>> getMessages(
    String channelId, {
    int page = 1,
    int limit = 50,
    int? before,
    int? after,
  }) async {
    final headers = await _apiClient.chatServiceHeaders();
    String query;
    if (before != null) {
      query = 'before=$before&limit=$limit';
    } else if (after != null) {
      query = 'after=$after&limit=$limit';
    } else {
      query = 'page=$page&limit=$limit';
    }
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/messages?$query');
    dev.log('[MESSAGE] GET $uri', name: 'MessageService');
    final response = await http.get(uri, headers: headers);
    dev.log('[MESSAGE] getMessages($channelId) → ${response.statusCode}  body=${response.body}', name: 'MessageService');

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
      dev.log('[MESSAGE] getMessages parsed ${items.length} messages', name: 'MessageService');
      return items
          .map((e) => MessageModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } else {
      dev.log('[MESSAGE] getMessages FAILED ${response.statusCode}: ${response.body}', name: 'MessageService');
      throw Exception('Failed to load messages (${response.statusCode})');
    }
  }

  Future<MessageModel> sendMessage(
    String channelId,
    String content, {
    int? threadId,
    int? replyToId,
    List<int>? mentions,
    String? clientMessageId,
  }) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/messages');

    final body = <String, dynamic>{'content': content};
    if (threadId != null) body['threadId'] = threadId;
    if (replyToId != null) body['replyToId'] = replyToId;
    if (mentions != null) body['mentions'] = mentions;
    if (clientMessageId != null) body['clientMessageId'] = clientMessageId;

    dev.log('[MESSAGE] POST $uri  body=${jsonEncode(body)}', name: 'MessageService');
    final response = await http.post(uri, headers: headers, body: jsonEncode(body));
    dev.log('[MESSAGE] sendMessage → ${response.statusCode}  body=${response.body}', name: 'MessageService');

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return MessageModel.fromJson(json);
    } else {
      dev.log('[MESSAGE] sendMessage FAILED: ${response.body}', name: 'MessageService');
      throw Exception('Failed to send message (${response.statusCode})');
    }
  }

  Future<MessageModel> editMessage(String messageId, String content) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/messages/$messageId');
    dev.log('[MESSAGE] PUT $uri  content=$content', name: 'MessageService');
    final response = await http.put(uri, headers: headers, body: jsonEncode({'content': content}));
    dev.log('[MESSAGE] editMessage($messageId) → ${response.statusCode}  body=${response.body}', name: 'MessageService');

    if (response.statusCode == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return MessageModel.fromJson(json);
    } else {
      dev.log('[MESSAGE] editMessage FAILED: ${response.body}', name: 'MessageService');
      throw Exception('Failed to edit message (${response.statusCode})');
    }
  }

  Future<void> deleteMessage(String messageId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/messages/$messageId');
    dev.log('[MESSAGE] DELETE $uri', name: 'MessageService');
    final response = await http.delete(uri, headers: headers);
    dev.log('[MESSAGE] deleteMessage($messageId) → ${response.statusCode}', name: 'MessageService');

    if (response.statusCode != 200 && response.statusCode != 204) {
      dev.log('[MESSAGE] deleteMessage FAILED: ${response.body}', name: 'MessageService');
      throw Exception('Failed to delete message (${response.statusCode})');
    }
  }
}
