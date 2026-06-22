import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/auth_storage.dart';
import '../../models/message_model.dart';
import '../../models/thread_model.dart';
import '../../providers/auth_provider.dart';
import '../../services/thread_service.dart';

class ThreadChatScreen extends StatefulWidget {
  final ThreadModel thread;
  const ThreadChatScreen({super.key, required this.thread});

  @override
  State<ThreadChatScreen> createState() => _ThreadChatScreenState();
}

class _ThreadChatScreenState extends State<ThreadChatScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  late final ThreadService _threadService;
  List<MessageModel> _messages = [];
  bool _loading = true;
  bool _sending = false;
  bool _initialScrollDone = false;

  @override
  void initState() {
    super.initState();
    _threadService = ThreadService(ApiClient(AuthStorage()));
    _load();
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final msgs = await _threadService.getMessages(widget.thread.id);
      msgs.sort((a, b) => _parseTs(a.createdAt).compareTo(_parseTs(b.createdAt)));
      _messages = msgs;
      if (_messages.isNotEmpty) {
        await _threadService.markRead(widget.thread.id, _messages.last.id);
      }
    } catch (_) {}
    if (mounted) {
      setState(() => _loading = false);
      WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());
    }
  }

  Future<void> _send() async {
    final content = _messageController.text.trim();
    if (content.isEmpty || _sending) return;
    _messageController.clear();
    setState(() => _sending = true);
    try {
      final msg = await _threadService.sendMessage(widget.thread.id, content);
      if (mounted) {
        setState(() {
          _messages = [..._messages, msg]
            ..sort((a, b) => _parseTs(a.createdAt).compareTo(_parseTs(b.createdAt)));
        });
        WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(e.toString().replaceFirst('Exception: ', '')),
            backgroundColor: Colors.red));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    }
  }

  DateTime _parseTs(String ts) {
    if (ts.isEmpty) return DateTime.fromMillisecondsSinceEpoch(0);
    final fixed = ts.replaceFirstMapped(RegExp(r'\.(\d{1,6})\d+'), (m) => '.${m[1]}');
    return DateTime.tryParse(fixed) ?? DateTime.fromMillisecondsSinceEpoch(0);
  }

  String _formatTime(String ts) {
    try {
      return DateFormat('HH:mm').format(_parseTs(ts).toLocal());
    } catch (_) {
      return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentUserId = context.watch<AuthProvider>().currentUser?.id;
    final colorScheme = Theme.of(context).colorScheme;

    if (!_initialScrollDone && !_loading && _messages.isNotEmpty) {
      _initialScrollDone = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.thread.name),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: Column(
        children: [
          if (_loading) const LinearProgressIndicator(),
          Expanded(
            child: _messages.isEmpty && !_loading
                ? const Center(
                    child: Text('No messages yet.\nSay something!',
                        textAlign: TextAlign.center))
                : RefreshIndicator(
                    onRefresh: _load,
                    child: ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: _messages.length,
                      itemBuilder: (_, i) {
                        final msg = _messages[i];
                        final isMe = msg.senderId == currentUserId;
                        return Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 3),
                          child: Row(
                            mainAxisAlignment: isMe
                                ? MainAxisAlignment.end
                                : MainAxisAlignment.start,
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              if (!isMe)
                                CircleAvatar(
                                  radius: 14,
                                  backgroundColor:
                                      colorScheme.secondaryContainer,
                                  child: Text(
                                    '${msg.senderId}'.substring(0, 1),
                                    style: TextStyle(
                                        fontSize: 10,
                                        color:
                                            colorScheme.onSecondaryContainer),
                                  ),
                                ),
                              const SizedBox(width: 6),
                              Flexible(
                                child: Container(
                                  constraints: BoxConstraints(
                                      maxWidth: MediaQuery.of(context)
                                              .size
                                              .width *
                                          0.72),
                                  decoration: BoxDecoration(
                                    color: msg.deleted
                                        ? colorScheme.surfaceContainerHigh
                                        : isMe
                                            ? const Color(0xFF0084FF)
                                            : colorScheme
                                                .surfaceContainerHighest,
                                    borderRadius: BorderRadius.only(
                                      topLeft: const Radius.circular(18),
                                      topRight: const Radius.circular(18),
                                      bottomLeft:
                                          Radius.circular(isMe ? 18 : 4),
                                      bottomRight:
                                          Radius.circular(isMe ? 4 : 18),
                                    ),
                                  ),
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 12, vertical: 8),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        msg.deleted
                                            ? 'This message was deleted'
                                            : msg.content,
                                        style: TextStyle(
                                          fontSize: 14,
                                          color: msg.deleted
                                              ? colorScheme.onSurfaceVariant
                                              : isMe
                                                  ? Colors.white
                                                  : colorScheme.onSurface,
                                          fontStyle: msg.deleted
                                              ? FontStyle.italic
                                              : FontStyle.normal,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        _formatTime(msg.createdAt),
                                        style: TextStyle(
                                          fontSize: 10,
                                          color: isMe
                                              ? Colors.white
                                                  .withValues(alpha: 0.7)
                                              : colorScheme.onSurface
                                                  .withValues(alpha: 0.5),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(width: 6),
                              if (isMe) const SizedBox(width: 28),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
          ),
          const Divider(height: 1),
          SafeArea(
            child: Padding(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _messageController,
                      maxLines: null,
                      decoration: InputDecoration(
                        hintText: 'Reply in ${widget.thread.name}…',
                        border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(24)),
                      ),
                      onSubmitted: (_) => _send(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    onPressed: _sending ? null : _send,
                    icon: _sending
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
