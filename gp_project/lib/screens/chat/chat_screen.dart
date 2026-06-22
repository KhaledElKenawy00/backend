import 'dart:developer' as dev;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/channel_model.dart';
import '../../models/message_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../widgets/message_bubble.dart';
import 'threads_screen.dart';

class ChatScreen extends StatefulWidget {
  final ChannelModel channel;

  const ChatScreen({super.key, required this.channel});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  bool _isTyping = false;
  bool _initialScrollDone = false;
  int _prevMessageCount = 0;
  late ChatProvider _chatProvider;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _chatProvider = context.read<ChatProvider>();
  }

  @override
  void initState() {
    super.initState();

    dev.log(
      '[CHAT_SCREEN] initState — channelId=${widget.channel.id}',
      name: 'ChatScreen',
    );

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _chatProvider.openChannel(widget.channel);
    });

    _messageController.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    _messageController.removeListener(_onTextChanged);
    _messageController.dispose();
    _scrollController.dispose();
    _chatProvider.closeChannel();
    super.dispose();
  }

  void _onTextChanged() {
    final chatProvider = context.read<ChatProvider>();
    final hasText = _messageController.text.isNotEmpty;

    if (hasText && !_isTyping) {
      _isTyping = true;
      chatProvider.sendTyping(widget.channel.id, typing: true);
    } else if (!hasText && _isTyping) {
      _isTyping = false;
      chatProvider.sendTyping(widget.channel.id, typing: false);
    }
  }

  void _sendMessage() {
    final content = _messageController.text.trim();
    if (content.isEmpty) return;

    final chatProvider = context.read<ChatProvider>();

    chatProvider.sendMessage(widget.channel.id, content);

    _messageController.clear();
    _isTyping = false;
    chatProvider.sendTyping(widget.channel.id, typing: false);

    WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());
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

  void _showEditDialog(MessageModel message) {
    final controller = TextEditingController(text: message.content);

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Message'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: null,
          decoration: const InputDecoration(border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              final newContent = controller.text.trim();

              if (newContent.isEmpty || newContent == message.content) {
                Navigator.pop(ctx);
                return;
              }

              Navigator.pop(ctx);

              await context.read<ChatProvider>().editMessage(
                message.id,
                newContent,
                widget.channel.id,
              );
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _confirmDelete(MessageModel message) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Message'),
        content: const Text('Are you sure you want to delete this message?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () async {
              Navigator.pop(ctx);

              await context.read<ChatProvider>().deleteMessage(
                message.id,
                widget.channel.id,
              );
            },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();
    final chatProvider = context.watch<ChatProvider>();

    final currentUserId = authProvider.currentUser?.id;
    final messages = chatProvider.messagesForChannel(widget.channel.id);
    final typingUsers = chatProvider.typingUsersForChannel(widget.channel.id);

    final channelName = widget.channel.displayName(
      currentUserId,
      userNames: chatProvider.userNames,
    );

    dev.log('CHANNEL TYPE = ${widget.channel.type}', name: 'DM_DEBUG');

    dev.log('CHANNEL MEMBERS = ${widget.channel.members}', name: 'DM_DEBUG');

    dev.log('USER NAMES = ${chatProvider.userNames}', name: 'DM_DEBUG');

    dev.log('DISPLAY NAME = $channelName', name: 'DM_DEBUG');

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients || messages.isEmpty) return;

      if (!_initialScrollDone) {
        _initialScrollDone = true;
        _prevMessageCount = messages.length;
        _scrollToBottom();
      } else if (messages.length > _prevMessageCount) {
        _prevMessageCount = messages.length;

        final pos = _scrollController.position;

        if (pos.pixels >= pos.maxScrollExtent - 400) {
          _scrollToBottom();
        }
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: widget.channel.isDm
            ? Row(
                children: [
                  CircleAvatar(
                    radius: 18,
                    child: Text(
                      channelName.isNotEmpty
                          ? channelName[0].toUpperCase()
                          : '?',
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(channelName, overflow: TextOverflow.ellipsis),
                  ),
                ],
              )
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(channelName),
                  Text(
                    'Group Channel',
                    style: TextStyle(
                      fontSize: 12,
                      color: Theme.of(
                        context,
                      ).colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                ],
              ),
        actions: [
          if (!widget.channel.isDm)
            IconButton(
              icon: const Icon(Icons.forum_outlined),
              tooltip: 'Threads',
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => ThreadsScreen(channel: widget.channel),
              )),
            ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              context.read<ChatProvider>().openChannel(widget.channel);
            },
          ),
        ],
      ),
      body: Column(
        children: [
          if (chatProvider.isLoadingMessages) const LinearProgressIndicator(),
          Expanded(
            child: messages.isEmpty && !chatProvider.isLoadingMessages
                ? Center(
                    child: Text(
                      'No messages yet.\nSay hello!',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Theme.of(
                          context,
                        ).colorScheme.onSurface.withValues(alpha: 0.5),
                      ),
                    ),
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    itemCount: messages.length,
                    itemBuilder: (ctx, i) {
                      final msg = messages[i];
                      final isMe = msg.senderId == currentUserId;

                      return MessageBubble(
                        message: msg,
                        isMe: isMe,
                        onEdit: () => _showEditDialog(msg),
                        onDelete: () => _confirmDelete(msg),
                        userNames: chatProvider.userNames,
                      );
                    },
                  ),
          ),
          if (typingUsers.isNotEmpty)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              alignment: Alignment.centerLeft,
              child: Text(
                typingUsers.length == 1
                    ? 'User ${typingUsers.first} is typing...'
                    : '${typingUsers.length} people are typing...',
              ),
            ),
          const Divider(height: 1),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _messageController,
                      maxLines: null,
                      decoration: InputDecoration(
                        hintText: 'Message $channelName...',
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(24),
                        ),
                      ),
                      onSubmitted: (_) => _sendMessage(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    onPressed: _sendMessage,
                    icon: const Icon(Icons.send),
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
