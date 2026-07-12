import 'dart:convert';
import 'dart:developer' as dev;
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import 'notification_service.dart';

typedef OnNotificationCallback = void Function(Map<String, dynamic> payload);
typedef OnMembershipUpdatedCallback = void Function(String type, int workspaceId);

class NotificationWsService {
  final ApiClient _apiClient;
  late final NotificationService _notifService;
  StompClient? _stompClient;
  bool _isConnected = false;
  OnNotificationCallback? _onNotification;
  OnMembershipUpdatedCallback? _onMembershipUpdated;

  NotificationWsService(this._apiClient) {
    _notifService = NotificationService(_apiClient);
  }

  bool get isConnected => _isConnected;

  Future<void> init({
    required OnNotificationCallback onNotification,
    OnMembershipUpdatedCallback? onMembershipUpdated,
  }) async {
    if (_isConnected) {
      dev.log('[NOTIF_WS] already connected — skip', name: 'NotificationWsService');
      return;
    }
    _onNotification = onNotification;
    _onMembershipUpdated = onMembershipUpdated;

    try {
      dev.log('[NOTIF_WS] fetching WS ticket…', name: 'NotificationWsService');
      final ticket = await _notifService.getWsTicket();
      final wsUrl = '${ApiConstants.notifWsUrl}?ticket=$ticket';
      dev.log('[NOTIF_WS] connecting to $wsUrl', name: 'NotificationWsService');

      _stompClient = StompClient(
        config: StompConfig(
          url: wsUrl,
          onConnect: (frame) {
            _isConnected = true;
            dev.log('[NOTIF_WS] STOMP connected — headers=${frame.headers}', name: 'NotificationWsService');
            _stompClient!.subscribe(
              destination: '/user/queue/notifications',
              callback: (f) {
                final body = f.body;
                dev.log('[NOTIF_WS] RAW frame: $body', name: 'NotificationWsService');
                if (body == null || body.isEmpty) return;
                try {
                  final json = jsonDecode(body) as Map<String, dynamic>;
                  final action = json['action'] as String? ?? '';
                  final payload = json['payload'] as Map<String, dynamic>? ?? {};
                  dev.log('[NOTIF_WS] action=$action payload=$payload', name: 'NotificationWsService');
                  if (action == 'NEW_NOTIFICATION') {
                    _onNotification?.call(payload);
                  } else if (action == 'MEMBERSHIP_UPDATED') {
                    final type = payload['type'] as String? ?? '';
                    final wsId = (payload['workspaceId'] as num?)?.toInt() ?? 0;
                    dev.log('[NOTIF_WS] MEMBERSHIP_UPDATED type=$type wsId=$wsId', name: 'NotificationWsService');
                    _onMembershipUpdated?.call(type, wsId);
                  }
                } catch (e) {
                  dev.log('[NOTIF_WS] parse error: $e  body=$body', name: 'NotificationWsService');
                }
              },
            );
          },
          onDisconnect: (_) {
            _isConnected = false;
            dev.log('[NOTIF_WS] STOMP disconnected', name: 'NotificationWsService');
          },
          onWebSocketError: (error) {
            _isConnected = false;
            dev.log('[NOTIF_WS] WS ERROR: $error', name: 'NotificationWsService');
          },
          onStompError: (frame) {
            dev.log('[NOTIF_WS] STOMP ERROR: ${frame.body}', name: 'NotificationWsService');
          },
          reconnectDelay: Duration.zero,
        ),
      );
      _stompClient!.activate();
      dev.log('[NOTIF_WS] StompClient.activate() called', name: 'NotificationWsService');
    } catch (e) {
      dev.log('[NOTIF_WS] init() ERROR: $e', name: 'NotificationWsService');
    }
  }

  void dispose() {
    dev.log('[NOTIF_WS] dispose()', name: 'NotificationWsService');
    _stompClient?.deactivate();
    _stompClient = null;
    _isConnected = false;
  }
}
