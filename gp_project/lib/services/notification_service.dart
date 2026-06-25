import 'dart:convert';
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/notification_model.dart';
import '../models/paginated_response.dart';

class NotificationService {
  final ApiClient _apiClient;

  NotificationService(this._apiClient);

  Future<PaginatedResponse<NotificationModel>> getNotifications({
    int page = 1,
    int size = 20,
  }) async {
    final headers = await _apiClient.notifServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.notifServiceBase}/api/notifications?page=$page&size=$size');
    final response = await http.get(uri, headers: headers);

    if (response.statusCode == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return PaginatedResponse.fromJson(json, NotificationModel.fromJson);
    } else {
      throw Exception('Failed to load notifications (${response.statusCode})');
    }
  }

  Future<int> getUnreadCount() async {
    final headers = await _apiClient.notifServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.notifServiceBase}/api/notifications/unread-count');
    final response = await http.get(uri, headers: headers);

    if (response.statusCode == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return json['unread'] as int? ?? 0;
    }
    return 0;
  }

  Future<void> markRead(String notificationId) async {
    final headers = await _apiClient.notifServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.notifServiceBase}/api/notifications/$notificationId/read');
    final response = await http.patch(uri, headers: headers);

    if (response.statusCode != 200 && response.statusCode != 204) {
      throw Exception(
          'Failed to mark notification as read (${response.statusCode})');
    }
  }

  Future<void> markAllRead() async {
    final headers = await _apiClient.notifServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.notifServiceBase}/api/notifications/read-all');
    final response = await http.patch(uri, headers: headers);

    if (response.statusCode != 200 && response.statusCode != 204) {
      throw Exception(
          'Failed to mark all notifications as read (${response.statusCode})');
    }
  }

  Future<void> deleteNotification(String notificationId) async {
    final headers = await _apiClient.notifServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.notifServiceBase}/api/notifications/$notificationId');
    final response = await http.delete(uri, headers: headers);

    if (response.statusCode != 200 && response.statusCode != 204) {
      throw Exception(
          'Failed to delete notification (${response.statusCode})');
    }
  }

  Future<String> getWsTicket() async {
    final headers = await _apiClient.notifServiceHeaders();
    final uri =
        Uri.parse('${ApiConstants.notifServiceBase}/api/notifications/ws-ticket');
    final response = await http.post(uri, headers: headers);

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return json['ticket'] as String;
    } else {
      throw Exception('Failed to get notification WS ticket');
    }
  }
}
