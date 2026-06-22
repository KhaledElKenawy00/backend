import 'dart:developer' as dev;
import 'package:flutter/foundation.dart';
import '../core/network/api_client.dart';
import '../models/notification_model.dart';
import '../services/notification_service.dart';
import '../services/notification_ws_service.dart';

class NotificationProvider extends ChangeNotifier {
  final ApiClient _apiClient;
  late final NotificationService _notificationService;
  late final NotificationWsService _wsService;

  List<NotificationModel> _notifications = [];
  int _unreadCount = 0;
  bool _isLoading = false;
  String? _errorMessage;

  NotificationProvider(this._apiClient) {
    _notificationService = NotificationService(_apiClient);
    _wsService = NotificationWsService(_apiClient);
  }

  List<NotificationModel> get notifications =>
      List.unmodifiable(_notifications);
  int get unreadCount => _unreadCount;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;

  Future<void> initWebSocket() async {
    dev.log('[NOTIF_PROVIDER] initWebSocket()', name: 'NotificationProvider');
    await _wsService.init(
      onNotification: (payload) {
        dev.log('[NOTIF_PROVIDER] NEW_NOTIFICATION received: $payload', name: 'NotificationProvider');
        _unreadCount++;
        notifyListeners();
        // Refresh list in background so the new notification appears
        load();
      },
    );
  }

  Future<void> load() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final result = await _notificationService.getNotifications();
      _notifications = result.items;
      _unreadCount = _notifications.where((n) => !n.read).length;
    } catch (e) {
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> fetchUnreadCount() async {
    try {
      _unreadCount = await _notificationService.getUnreadCount();
      notifyListeners();
    } catch (_) {}
  }

  Future<void> markRead(int notificationId) async {
    try {
      await _notificationService.markRead(notificationId);
      final idx = _notifications.indexWhere((n) => n.id == notificationId);
      if (idx != -1 && !_notifications[idx].read) {
        final updated = List<NotificationModel>.from(_notifications);
        updated[idx] = updated[idx].copyWith(read: true);
        _notifications = updated;
        if (_unreadCount > 0) _unreadCount--;
        notifyListeners();
      }
    } catch (e) {
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      notifyListeners();
    }
  }

  Future<void> markAllRead() async {
    try {
      await _notificationService.markAllRead();
      _notifications = _notifications.map((n) => n.copyWith(read: true)).toList();
      _unreadCount = 0;
      notifyListeners();
    } catch (e) {
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      notifyListeners();
    }
  }

  Future<void> delete(int notificationId) async {
    try {
      await _notificationService.deleteNotification(notificationId);
      final removed = _notifications.firstWhere(
        (n) => n.id == notificationId,
        orElse: () => NotificationModel(
            id: -1, title: '', body: '', read: true, createdAt: ''),
      );
      _notifications =
          _notifications.where((n) => n.id != notificationId).toList();
      if (!removed.read && _unreadCount > 0) _unreadCount--;
      notifyListeners();
    } catch (e) {
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      notifyListeners();
    }
  }

  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _wsService.dispose();
    super.dispose();
  }
}
