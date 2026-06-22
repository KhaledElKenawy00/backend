import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/message_model.dart';
import '../models/thread_model.dart';

class ThreadService {
  final ApiClient _apiClient;
  ThreadService(this._apiClient);

  Future<ThreadModel> createThread(String channelId, String name) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/threads');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({'name': name}));
    dev.log('[THREAD] createThread → ${response.statusCode}', name: 'ThreadService');
    if (response.statusCode == 200 || response.statusCode == 201) {
      return ThreadModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    throw Exception('Failed to create thread (${response.statusCode})');
  }

  Future<List<ThreadModel>> getThreads(String channelId, {int page = 1, int limit = 20}) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/channels/$channelId/threads?page=$page&limit=$limit');
    final response = await http.get(uri, headers: headers);
    dev.log('[THREAD] getThreads → ${response.statusCode}', name: 'ThreadService');
    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final items = body is List ? body : (body['items'] ?? body['content'] ?? body['data'] ?? []);
      return (items as List).map((e) => ThreadModel.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception('Failed to load threads (${response.statusCode})');
  }

  Future<ThreadModel> getThread(String threadId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/threads/$threadId');
    final response = await http.get(uri, headers: headers);
    if (response.statusCode == 200) {
      return ThreadModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    throw Exception('Failed to load thread (${response.statusCode})');
  }

  Future<void> deleteThread(String threadId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/threads/$threadId');
    final response = await http.delete(uri, headers: headers);
    if (response.statusCode != 200 && response.statusCode != 204) {
      throw Exception('Failed to delete thread (${response.statusCode})');
    }
  }

  Future<List<MessageModel>> getMessages(String threadId, {int page = 1, int limit = 30}) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.chatServiceBase}/api/chat/threads/$threadId/messages?page=$page&limit=$limit');
    final response = await http.get(uri, headers: headers);
    dev.log('[THREAD] getMessages → ${response.statusCode}', name: 'ThreadService');
    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final items = body is List ? body : (body['items'] ?? body['content'] ?? body['data'] ?? []);
      return (items as List).map((e) => MessageModel.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception('Failed to load thread messages (${response.statusCode})');
  }

  Future<void> markRead(String threadId, String messageId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/threads/$threadId/read');
    await http.post(uri, headers: headers, body: jsonEncode({'lastReadMessageId': messageId}));
  }

  Future<MessageModel> sendMessage(String threadId, String content) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/threads/$threadId/messages');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({'content': content}));
    dev.log('[THREAD] sendMessage → ${response.statusCode}', name: 'ThreadService');
    if (response.statusCode == 200 || response.statusCode == 201) {
      return MessageModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    throw Exception('Failed to send message (${response.statusCode})');
  }

  Future<int> getUnreadCount(String threadId) async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri = Uri.parse('${ApiConstants.chatServiceBase}/api/chat/threads/$threadId/unread');
    final response = await http.get(uri, headers: headers);
    if (response.statusCode == 200) {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      return body['count'] as int? ?? body['unread'] as int? ?? 0;
    }
    return 0;
  }
}
