import 'dart:developer' as dev;
import 'package:flutter/foundation.dart';
import '../core/network/api_client.dart';
import '../models/channel_model.dart';
import '../models/message_model.dart';
import '../services/channel_service.dart';
import '../services/message_service.dart';
import '../services/user_service.dart';
import '../services/websocket_service.dart';

class ChatProvider extends ChangeNotifier {
  final ApiClient _apiClient;
  late final ChannelService _channelService;
  late final MessageService _messageService;
  late final UserService _userService;
  late final WebSocketService _wsService;

  List<ChannelModel> _channels = [];
  List<ChannelModel> _dms = [];
  final Map<String, List<MessageModel>> _messages = {};
  final Map<String, Set<int>> _typingUsers = {};
  final Map<String, int> _unreadCounts = {};
  Map<int, String> _userNames = {};

  ChannelModel? _activeChannel;
  bool _isLoadingChannels = false;
  bool _isLoadingMessages = false;
  String? _errorMessage;
  int? _currentUserId;

  ChatProvider(this._apiClient) {
    _channelService = ChannelService(_apiClient);
    _messageService = MessageService(_apiClient);
    _userService = UserService(_apiClient);
    _wsService = WebSocketService(_apiClient);
    dev.log('[CHAT_PROVIDER] created', name: 'ChatProvider');
  }

  List<ChannelModel> get channels => List.unmodifiable(_channels);
  List<ChannelModel> get dms => List.unmodifiable(_dms);
  Map<String, List<MessageModel>> get messages => _messages;
  ChannelModel? get activeChannel => _activeChannel;
  bool get isLoadingChannels => _isLoadingChannels;
  bool get isLoadingMessages => _isLoadingMessages;
  String? get errorMessage => _errorMessage;
  WebSocketService get wsService => _wsService;

  List<MessageModel> messagesForChannel(String channelId) =>
      List.unmodifiable(_messages[channelId] ?? []);

  Set<int> typingUsersForChannel(String channelId) =>
      Set.unmodifiable(_typingUsers[channelId] ?? {});

  int unreadCount(String channelId) => _unreadCounts[channelId] ?? 0;

  Map<int, String> get userNames => Map.unmodifiable(_userNames);
  String userNameFor(int userId) => _userNames[userId] ?? 'User $userId';

  Future<void> loadUserNames() async {
    try {
      final users = await _userService.getUsers();
      _userNames = {for (final u in users) u.id: u.fullName};
      dev.log(
        '[CHAT_PROVIDER] loadUserNames — ${_userNames.length} users: $_userNames',
        name: 'ChatProvider',
      );
      notifyListeners();
    } catch (e) {
      dev.log('[CHAT_PROVIDER] loadUserNames failed: $e', name: 'ChatProvider');
    }
  }

  Future<void> initWebSocket(int userId, String role) async {
    dev.log(
      '[CHAT_PROVIDER] initWebSocket — userId=$userId  role=$role',
      name: 'ChatProvider',
    );
    _currentUserId = userId;
    await _wsService.init(
      userId: userId,
      role: role,
      onError: (error) {
        dev.log(
          '[CHAT_PROVIDER] WS error callback: $error',
          name: 'ChatProvider',
        );
        _errorMessage = error;
        notifyListeners();
      },
    );
  }

  Future<void> loadChannels(int workspaceId, {bool silent = false}) async {
    dev.log(
      '[CHAT_PROVIDER] loadChannels(workspaceId=$workspaceId, silent=$silent)',
      name: 'ChatProvider',
    );
    if (!silent) {
      _isLoadingChannels = true;
      _errorMessage = null;
      notifyListeners();
    }

    try {
      _channels = await _channelService.getChannels(workspaceId: workspaceId);
      dev.log(
        '[CHAT_PROVIDER] loadChannels — got ${_channels.length} channels: ${_channels.map((c) => c.id).toList()}',
        name: 'ChatProvider',
      );
      for (final ch in _channels) {
        try {
          _unreadCounts[ch.id] = await _channelService.getUnreadCount(ch.id);
        } catch (e) {
          dev.log(
            '[CHAT_PROVIDER] getUnreadCount(${ch.id}) failed: $e',
            name: 'ChatProvider',
          );
        }
      }
    } catch (e) {
      dev.log('[CHAT_PROVIDER] loadChannels ERROR: $e', name: 'ChatProvider');
      if (!silent) _errorMessage = e.toString().replaceFirst('Exception: ', '');
    } finally {
      if (!silent) _isLoadingChannels = false;
      notifyListeners();
    }
  }

  Future<void> loadDms() async {
    dev.log('[CHAT_PROVIDER] loadDms()', name: 'ChatProvider');
    _isLoadingChannels = true;
    _errorMessage = null;
    notifyListeners();

    try {
      _dms = await _channelService.getDms();
      dev.log(
        '[CHAT_PROVIDER] loadDms — got ${_dms.length} DMs',
        name: 'ChatProvider',
      );
      for (final dm in _dms) {
        try {
          _unreadCounts[dm.id] = await _channelService.getUnreadCount(dm.id);
        } catch (e) {
          dev.log(
            '[CHAT_PROVIDER] getUnreadCount(${dm.id}) failed: $e',
            name: 'ChatProvider',
          );
        }
      }
    } catch (e) {
      dev.log('[CHAT_PROVIDER] loadDms ERROR: $e', name: 'ChatProvider');
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _isLoadingChannels = false;
      notifyListeners();
    }
  }

  // Java Instant serialises with nanosecond precision (9 decimal places).
  // Dart's DateTime.tryParse only handles up to 6 (microseconds).
  // Truncate anything beyond 6 fractional digits so Dart can parse it.
  static DateTime _parseTimestamp(String ts) {
    if (ts.isEmpty) return DateTime.fromMillisecondsSinceEpoch(0);
    final fixed = ts.replaceFirstMapped(
      RegExp(r'\.(\d{1,6})\d+'),
      (m) => '.${m[1]}',
    );
    return DateTime.tryParse(fixed) ??
        DateTime.tryParse(ts) ??
        DateTime.fromMillisecondsSinceEpoch(0);
  }

  List<MessageModel> _sortedByTime(List<MessageModel> msgs) {
    final sorted = List<MessageModel>.from(msgs);
    sorted.sort(
      (a, b) =>
          _parseTimestamp(a.createdAt).compareTo(_parseTimestamp(b.createdAt)),
    );
    return sorted;
  }

  Future<void> openChannel(ChannelModel channel) async {
    dev.log(
      '[CHAT_PROVIDER] openChannel — id=${channel.id}  name=${channel.name}',
      name: 'ChatProvider',
    );

    _activeChannel = channel;
    _isLoadingMessages = true;
    notifyListeners();

    try {
      // جلب الرسائل من API
      final msgs = await _messageService.getMessages(channel.id);

      dev.log(
        '[CHAT_PROVIDER] openChannel — loaded ${msgs.length} messages',
        name: 'ChatProvider',
      );

      // ترتيب الرسائل حسب الوقت (الأقدم → الأحدث)
      final sorted = _sortedByTime(msgs);

      // DEBUG: اطبع الترتيب بعد الـ sorting
      dev.log(
        '================ SORT DEBUG START ================',
        name: 'SORT_CHECK',
      );

      for (final m in sorted) {
        dev.log(
          'AFTER SORT => ${m.content} | ${m.createdAt}',
          name: 'SORT_CHECK',
        );
      }

      dev.log(
        '================ SORT DEBUG END ================',
        name: 'SORT_CHECK',
      );

      // حفظ الرسائل بعد الترتيب
      _messages[channel.id] = sorted;

      // تعليم آخر رسالة (الأحدث) كمقروءة
      if (sorted.isNotEmpty) {
        try {
          await _channelService.markChannelRead(
            channel.id,
            sorted.last.id, // أحدث رسالة وليس msgs.last
          );

          _unreadCounts[channel.id] = 0;

          dev.log(
            '[CHAT_PROVIDER] markChannelRead success → lastMessage=${sorted.last.id}',
            name: 'ChatProvider',
          );
        } catch (e) {
          dev.log(
            '[CHAT_PROVIDER] markChannelRead failed: $e',
            name: 'ChatProvider',
          );
        }
      }

      // الاشتراك في websocket
      dev.log(
        '[CHAT_PROVIDER] calling subscribeToChannel(${channel.id})',
        name: 'ChatProvider',
      );

      _wsService.subscribeToChannel(channel.id, (action, payload) {
        dev.log(
          '[CHAT_PROVIDER] WS event received — action=$action  payload=$payload',
          name: 'ChatProvider',
        );

        _handleWsEvent(channel.id, action, payload);
      });
    } catch (e) {
      dev.log('[CHAT_PROVIDER] openChannel ERROR: $e', name: 'ChatProvider');

      _errorMessage = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _isLoadingMessages = false;
      notifyListeners();
    }
  }

  void closeChannel() {
    if (_activeChannel != null) {
      dev.log(
        '[CHAT_PROVIDER] closeChannel — id=${_activeChannel!.id}',
        name: 'ChatProvider',
      );
      _wsService.unsubscribeFromChannel(_activeChannel!.id);
      _typingUsers.remove(_activeChannel!.id);
      _activeChannel = null;
      // intentionally no notifyListeners() — called from dispose(), tree may be locked
    }
  }

  void sendMessage(String channelId, String content) {
    dev.log(
      '[CHAT_PROVIDER] sendMessage — channelId=$channelId  content=$content',
      name: 'ChatProvider',
    );
    _wsService.sendMessage(channelId, content);
  }

  Future<void> editMessage(
    String messageId,
    String content,
    String channelId,
  ) async {
    dev.log(
      '[CHAT_PROVIDER] editMessage — id=$messageId  channelId=$channelId',
      name: 'ChatProvider',
    );
    try {
      final updated = await _messageService.editMessage(messageId, content);
      final msgs = _messages[channelId];
      if (msgs != null) {
        final idx = msgs.indexWhere((m) => m.id == messageId);
        if (idx != -1) {
          msgs[idx] = updated;
          notifyListeners();
        }
      }
    } catch (e) {
      dev.log('[CHAT_PROVIDER] editMessage ERROR: $e', name: 'ChatProvider');
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      notifyListeners();
    }
  }

  Future<void> deleteMessage(String messageId, String channelId) async {
    dev.log(
      '[CHAT_PROVIDER] deleteMessage — id=$messageId  channelId=$channelId',
      name: 'ChatProvider',
    );
    try {
      await _messageService.deleteMessage(messageId);
      final msgs = _messages[channelId];
      if (msgs != null) {
        final idx = msgs.indexWhere((m) => m.id == messageId);
        if (idx != -1) {
          msgs[idx] = msgs[idx].copyWith(deleted: true);
          notifyListeners();
        }
      }
    } catch (e) {
      dev.log('[CHAT_PROVIDER] deleteMessage ERROR: $e', name: 'ChatProvider');
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      notifyListeners();
    }
  }

  void sendTyping(String channelId, {bool typing = true}) {
    _wsService.sendTyping(channelId, typing: typing);
  }

  Future<ChannelModel> createChannel(
    String name,
    int workspaceId,
    List<int> members,
  ) async {
    dev.log(
      '[CHAT_PROVIDER] createChannel — name=$name  workspaceId=$workspaceId  members=$members',
      name: 'ChatProvider',
    );
    final channel = await _channelService.createChannel(
      name: name,
      workspaceId: workspaceId,
      members: members,
    );
    dev.log(
      '[CHAT_PROVIDER] createChannel success — id=${channel.id}',
      name: 'ChatProvider',
    );
    _channels = [channel, ..._channels];
    notifyListeners();
    return channel;
  }

  Future<ChannelModel> createOrGetDm(int targetUserId) async {
    dev.log(
      '[CHAT_PROVIDER] createOrGetDm — targetUserId=$targetUserId',
      name: 'ChatProvider',
    );
    final channel = await _channelService.createOrGetDm(targetUserId);
    dev.log(
      '[CHAT_PROVIDER] createOrGetDm success — id=${channel.id}',
      name: 'ChatProvider',
    );
    if (!_dms.any((d) => d.id == channel.id)) {
      _dms = [channel, ..._dms];
      notifyListeners();
    }
    return channel;
  }

  void _handleWsEvent(
    String channelId,
    String action,
    Map<String, dynamic> payload,
  ) {
    dev.log(
      '[CHAT_PROVIDER] _handleWsEvent — channelId=$channelId  action=$action',
      name: 'ChatProvider',
    );
    switch (action) {
      case 'NEW_MESSAGE':
        final msg = MessageModel.fromJson(payload);
        dev.log(
          '[CHAT_PROVIDER] NEW_MESSAGE — id=${msg.id}  senderId=${msg.senderId}  content=${msg.content}',
          name: 'ChatProvider',
        );
        final msgs = _messages[channelId] ?? [];
        final exists = msgs.any(
          (m) =>
              m.id == msg.id ||
              (msg.clientMessageId != null &&
                  m.clientMessageId == msg.clientMessageId),
        );
        dev.log(
          '[CHAT_PROVIDER] NEW_MESSAGE — exists=$exists  currentMsgCount=${msgs.length}',
          name: 'ChatProvider',
        );
        if (!exists) {
          _messages[channelId] = _sortedByTime([...msgs, msg]);
          if (_activeChannel?.id == channelId) {
            _channelService.markChannelRead(channelId, msg.id).catchError((e) {
              dev.log(
                '[CHAT_PROVIDER] markChannelRead failed: $e',
                name: 'ChatProvider',
              );
            });
          } else {
            _unreadCounts[channelId] = (_unreadCounts[channelId] ?? 0) + 1;
          }
        }
        break;

      case 'EDIT_MESSAGE':
        final updated = MessageModel.fromJson(payload);
        dev.log(
          '[CHAT_PROVIDER] EDIT_MESSAGE — id=${updated.id}',
          name: 'ChatProvider',
        );
        final msgs = _messages[channelId];
        if (msgs != null) {
          final idx = msgs.indexWhere((m) => m.id == updated.id);
          if (idx != -1) {
            final newList = List<MessageModel>.from(msgs);
            newList[idx] = updated;
            _messages[channelId] = newList;
          }
        }
        break;

      case 'DELETE_MESSAGE':
        final msgId =
            payload['id']?.toString() ?? payload['messageId']?.toString();
        dev.log(
          '[CHAT_PROVIDER] DELETE_MESSAGE — msgId=$msgId  rawPayload=$payload',
          name: 'ChatProvider',
        );
        if (msgId != null) {
          final msgs = _messages[channelId];
          if (msgs != null) {
            final idx = msgs.indexWhere((m) => m.id == msgId);
            if (idx != -1) {
              final newList = List<MessageModel>.from(msgs);
              newList[idx] = newList[idx].copyWith(deleted: true);
              _messages[channelId] = newList;
            }
          }
        }
        break;

      case 'TYPING':
        // backend sends `userId` (not `senderId`) in TypingNotification
        final senderId =
            payload['userId'] as int? ?? payload['senderId'] as int?;
        final isTyping = payload['typing'] as bool? ?? false;
        dev.log(
          '[CHAT_PROVIDER] TYPING — senderId=$senderId  isTyping=$isTyping  currentUserId=$_currentUserId',
          name: 'ChatProvider',
        );
        if (senderId != null && senderId != _currentUserId) {
          _typingUsers[channelId] ??= {};
          if (isTyping) {
            _typingUsers[channelId]!.add(senderId);
          } else {
            _typingUsers[channelId]!.remove(senderId);
          }
        }
        break;

      default:
        dev.log(
          '[CHAT_PROVIDER] UNKNOWN action=$action  payload=$payload',
          name: 'ChatProvider',
        );
    }
    notifyListeners();
  }

  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }

  @override
  void dispose() {
    dev.log('[CHAT_PROVIDER] dispose()', name: 'ChatProvider');
    _wsService.dispose();
    super.dispose();
  }
}
