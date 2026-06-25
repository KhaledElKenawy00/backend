import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import 'package:stomp_dart_client/stomp_dart_client.dart';
import 'package:uuid/uuid.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';

typedef OnMessageCallback = void Function(
    String action, Map<String, dynamic> payload);
typedef OnErrorCallback = void Function(String error);

class WebSocketService {
  final ApiClient _apiClient;
  StompClient? _stompClient;
  bool _isConnected = false;
  final Map<String, StompUnsubscribe> _subscriptions = {};
  final Map<String, StompUnsubscribe> _typingSubscriptions = {};
  // subscriptions requested before WS was ready — flushed on connect
  final Map<String, OnMessageCallback> _pendingChannels = {};
  final _uuid = const Uuid();

  WebSocketService(this._apiClient);

  bool get isConnected => _isConnected;

  Future<String> _getWsTicket() async {
    final headers = await _apiClient.chatServiceHeaders();
    final uri =
        Uri.parse('${ApiConstants.chatServiceBase}/api/chat/ws-ticket');
    dev.log('[WS] POST $uri (get ticket)', name: 'WebSocketService');
    final response = await http.post(uri, headers: headers);
    dev.log('[WS] ws-ticket → ${response.statusCode}  body=${response.body}', name: 'WebSocketService');

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final ticket = json['ticket'] as String;
      dev.log('[WS] ticket received: $ticket', name: 'WebSocketService');
      return ticket;
    } else {
      dev.log('[WS] FAILED to get ticket: ${response.statusCode} ${response.body}', name: 'WebSocketService');
      throw Exception('Failed to get WebSocket ticket (${response.statusCode})');
    }
  }

  Future<void> init({
    required int userId,
    required String role,
    OnErrorCallback? onError,
    VoidCallback? onConnected,
  }) async {
    if (_isConnected) {
      dev.log('[WS] init() called but already connected — skipping', name: 'WebSocketService');
      return;
    }

    dev.log('[WS] init() — userId=$userId role=$role', name: 'WebSocketService');
    final ticket = await _getWsTicket();
    final wsUrl = '${ApiConstants.chatWsUrl}?ticket=$ticket';
    dev.log('[WS] connecting to $wsUrl', name: 'WebSocketService');

    _stompClient = StompClient(
      config: StompConfig(
        url: wsUrl,
        onConnect: (frame) {
          _isConnected = true;
          dev.log('[WS] STOMP connected — headers=${frame.headers}', name: 'WebSocketService');
          onConnected?.call();

          _stompClient?.subscribe(
            destination: '/user/queue/errors',
            callback: (frame) {
              final body = frame.body;
              dev.log('[WS] /user/queue/errors received: $body', name: 'WebSocketService');
              if (body != null) onError?.call(body);
            },
          );

          // flush any subscriptions that were requested before WS was ready
          final pending = Map.of(_pendingChannels);
          _pendingChannels.clear();
          dev.log('[WS] flushing ${pending.length} pending subscriptions', name: 'WebSocketService');
          for (final entry in pending.entries) {
            _doSubscribe(entry.key, entry.value);
          }
        },
        onDisconnect: (frame) {
          _isConnected = false;
          dev.log('[WS] STOMP disconnected', name: 'WebSocketService');
        },
        onWebSocketError: (error) {
          _isConnected = false;
          dev.log('[WS] WebSocket ERROR: $error', name: 'WebSocketService');
          onError?.call(error.toString());
        },
        onStompError: (frame) {
          dev.log('[WS] STOMP ERROR: ${frame.body}', name: 'WebSocketService');
          onError?.call(frame.body ?? 'STOMP error');
        },
        reconnectDelay: const Duration(seconds: 5),
      ),
    );

    _stompClient!.activate();
    dev.log('[WS] StompClient.activate() called', name: 'WebSocketService');
  }

  void subscribeToChannel(String channelId, OnMessageCallback onMessage) {
    dev.log('[WS] subscribeToChannel($channelId) — connected=$_isConnected', name: 'WebSocketService');
    if (_stompClient == null || !_isConnected) {
      dev.log('[WS] WS not ready — queuing channel $channelId in pending', name: 'WebSocketService');
      _pendingChannels[channelId] = onMessage;
      return;
    }
    _doSubscribe(channelId, onMessage);
  }

  void _doSubscribe(String channelId, OnMessageCallback onMessage) {
    if (_subscriptions.containsKey(channelId)) {
      dev.log('[WS] _doSubscribe($channelId) — already subscribed, skip', name: 'WebSocketService');
      return;
    }

    dev.log('[WS] subscribing to /topic/channel/$channelId', name: 'WebSocketService');

    // message events
    final unsubMsg = _stompClient!.subscribe(
      destination: '/topic/channel/$channelId',
      callback: (frame) {
        final body = frame.body;
        dev.log('[WS] RAW frame on /topic/channel/$channelId: $body', name: 'WebSocketService');
        if (body == null || body.isEmpty) return;
        try {
          final json = jsonDecode(body) as Map<String, dynamic>;
          final action = json['action'] as String? ?? '';
          final payload = json['payload'] as Map<String, dynamic>? ?? {};
          dev.log('[WS] EVENT action=$action  payload=$payload', name: 'WebSocketService');
          onMessage(action, payload);
        } catch (e) {
          dev.log('[WS] ERROR parsing frame: $e  body=$body', name: 'WebSocketService');
        }
      },
    );
    _subscriptions[channelId] = unsubMsg;

    dev.log('[WS] subscribing to /topic/channel/$channelId/typing', name: 'WebSocketService');

    // typing events (separate topic on backend)
    final unsubTyping = _stompClient!.subscribe(
      destination: '/topic/channel/$channelId/typing',
      callback: (frame) {
        final body = frame.body;
        dev.log('[WS] RAW frame on /topic/channel/$channelId/typing: $body', name: 'WebSocketService');
        if (body == null || body.isEmpty) return;
        try {
          final json = jsonDecode(body) as Map<String, dynamic>;
          final action = json['action'] as String? ?? '';
          final payload = json['payload'] as Map<String, dynamic>? ?? {};
          dev.log('[WS] TYPING EVENT action=$action  payload=$payload', name: 'WebSocketService');
          onMessage(action, payload);
        } catch (e) {
          dev.log('[WS] ERROR parsing typing frame: $e  body=$body', name: 'WebSocketService');
        }
      },
    );
    _typingSubscriptions[channelId] = unsubTyping;
    dev.log('[WS] _doSubscribe($channelId) complete', name: 'WebSocketService');
  }

  void unsubscribeFromChannel(String channelId) {
    dev.log('[WS] unsubscribeFromChannel($channelId)', name: 'WebSocketService');
    _pendingChannels.remove(channelId);
    _subscriptions.remove(channelId)?.call();
    _typingSubscriptions.remove(channelId)?.call();
  }

  void sendMessage(
    String channelId,
    String content, {
    String? threadId,
    String? replyToId,
    List<int>? mentions,
  }) {
    dev.log('[WS] sendMessage — channelId=$channelId  connected=$_isConnected  content=$content', name: 'WebSocketService');
    if (_stompClient == null || !_isConnected) {
      dev.log('[WS] sendMessage DROPPED — not connected!', name: 'WebSocketService');
      return;
    }

    final clientMessageId = _uuid.v4();
    final body = <String, dynamic>{
      'channelId': channelId,
      'content': content,
      'clientMessageId': clientMessageId,
    };
    if (threadId != null) body['threadId'] = threadId;
    if (replyToId != null) body['replyToId'] = replyToId;
    if (mentions != null && mentions.isNotEmpty) body['mentions'] = mentions;

    dev.log('[WS] STOMP send /app/chat/send  body=${jsonEncode(body)}', name: 'WebSocketService');
    _stompClient!.send(
      destination: '/app/chat/send',
      body: jsonEncode(body),
    );
  }

  void sendTyping(String channelId, {bool typing = true, String? threadId}) {
    if (_stompClient == null || !_isConnected) return;

    final body = <String, dynamic>{
      'channelId': channelId,
      'typing': typing,
    };
    if (threadId != null) body['threadId'] = threadId;

    dev.log('[WS] STOMP send /app/chat/typing  channelId=$channelId  typing=$typing', name: 'WebSocketService');
    _stompClient!.send(
      destination: '/app/chat/typing',
      body: jsonEncode(body),
    );
  }

  void dispose() {
    dev.log('[WS] dispose() — clearing ${_subscriptions.length} subscriptions', name: 'WebSocketService');
    for (final unsubscribe in _subscriptions.values) {
      unsubscribe();
    }
    for (final unsubscribe in _typingSubscriptions.values) {
      unsubscribe();
    }
    _subscriptions.clear();
    _typingSubscriptions.clear();
    _pendingChannels.clear();
    _stompClient?.deactivate();
    _stompClient = null;
    _isConnected = false;
  }
}

typedef VoidCallback = void Function();
