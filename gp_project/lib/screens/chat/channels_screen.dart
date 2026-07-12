import 'dart:developer' as dev;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../providers/workspace_provider.dart';
import '../../services/user_service.dart';
import '../../widgets/channel_tile.dart';
import 'chat_screen.dart';

class ChannelsScreen extends StatefulWidget {
  const ChannelsScreen({super.key});

  @override
  State<ChannelsScreen> createState() => _ChannelsScreenState();
}

class _ChannelsScreenState extends State<ChannelsScreen> {
  int get _workspaceId =>
      context.read<WorkspaceProvider>().activeWorkspaceId ?? 1;

  Future<void> _refresh() async {
    dev.log('[CHANNELS_SCREEN] refresh — workspaceId=$_workspaceId', name: 'ChannelsScreen');
    await context.read<ChatProvider>().loadChannels(_workspaceId);
  }

  Future<void> _showCreateChannelDialog() async {
    dev.log('[CHANNELS_SCREEN] _showCreateChannelDialog opened', name: 'ChannelsScreen');
    final authProvider = context.read<AuthProvider>();
    final userService = UserService(authProvider.apiClient);
    final currentUserId = authProvider.currentUser?.id;
    dev.log('[CHANNELS_SCREEN] currentUserId=$currentUserId', name: 'ChannelsScreen');

    final nameController = TextEditingController();
    List<UserModel> allUsers = [];
    final Set<int> selectedIds = {};
    bool loadingUsers = true;
    String? loadError;

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) {
          if (loadingUsers && allUsers.isEmpty && loadError == null) {
            dev.log('[CHANNELS_SCREEN] fetching user list…', name: 'ChannelsScreen');
            userService.getUsers().then((users) {
              dev.log('[CHANNELS_SCREEN] getUsers() returned ${users.length} users (excluding self)', name: 'ChannelsScreen');
              setDialogState(() {
                allUsers = users.where((u) => u.id != currentUserId).toList();
                loadingUsers = false;
              });
            }).catchError((e) {
              dev.log('[CHANNELS_SCREEN] getUsers() ERROR: $e', name: 'ChannelsScreen');
              setDialogState(() {
                loadError = e.toString().replaceFirst('Exception: ', '');
                loadingUsers = false;
              });
            });
          }

          return AlertDialog(
            title: const Text('New Channel'),
            content: SizedBox(
              width: double.maxFinite,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                  TextField(
                    controller: nameController,
                    autofocus: true,
                    decoration: const InputDecoration(
                      labelText: 'Channel name',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Add members',
                    style: Theme.of(ctx).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 8),
                  if (loadingUsers)
                    const Center(child: CircularProgressIndicator())
                  else if (loadError != null)
                    Text(loadError!, style: const TextStyle(color: Colors.red))
                  else if (allUsers.isEmpty)
                    const Text('No other users found')
                  else
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 220),
                      child: ListView.builder(
                        shrinkWrap: true,
                        itemCount: allUsers.length,
                        itemBuilder: (_, i) {
                          final u = allUsers[i];
                          final checked = selectedIds.contains(u.id);
                          return CheckboxListTile(
                            dense: true,
                            value: checked,
                            title: Text(u.fullName),
                            subtitle: Text(
                              u.email,
                              style: const TextStyle(fontSize: 12),
                            ),
                            onChanged: (v) {
                              setDialogState(() {
                                if (v == true) {
                                  selectedIds.add(u.id);
                                } else {
                                  selectedIds.remove(u.id);
                                }
                              });
                            },
                          );
                        },
                      ),
                    ),
                ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: loadingUsers
                    ? null
                    : () async {
                        final name = nameController.text.trim();
                        dev.log('[CHANNELS_SCREEN] Create pressed — name="$name"  selectedIds=$selectedIds', name: 'ChannelsScreen');
                        if (name.isEmpty) return;
                        if (selectedIds.isEmpty) {
                          ScaffoldMessenger.of(ctx).showSnackBar(
                            const SnackBar(
                              content: Text('اختر عضو واحد على الأقل'),
                              backgroundColor: Colors.orange,
                            ),
                          );
                          return;
                        }
                        Navigator.pop(ctx);
                        try {
                          dev.log('[CHANNELS_SCREEN] calling createChannel name=$name  members=${selectedIds.toList()}', name: 'ChannelsScreen');
                          final channel = await context
                              .read<ChatProvider>()
                              .createChannel(name, _workspaceId, selectedIds.toList());
                          dev.log('[CHANNELS_SCREEN] createChannel success — id=${channel.id}', name: 'ChannelsScreen');
                          if (!mounted) return;
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => ChatScreen(channel: channel),
                            ),
                          );
                        } catch (e) {
                          dev.log('[CHANNELS_SCREEN] createChannel ERROR: $e', name: 'ChannelsScreen');
                          if (!mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(e.toString().replaceFirst('Exception: ', '')),
                              backgroundColor: Colors.red,
                            ),
                          );
                        }
                      },
                child: const Text('Create'),
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();
    final chatProvider = context.watch<ChatProvider>();
    final currentUserId = authProvider.currentUser?.id;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Channels'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
        ],
      ),
      body: chatProvider.isLoadingChannels
          ? const Center(child: CircularProgressIndicator())
          : chatProvider.channels.isEmpty
              ? RefreshIndicator(
                  onRefresh: _refresh,
                  child: ListView(
                    children: [
                      SizedBox(
                        height: MediaQuery.of(context).size.height * 0.6,
                        child: Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.tag,
                                  size: 64,
                                  color: Theme.of(context)
                                      .colorScheme
                                      .onSurface
                                      .withValues(alpha: 0.3)),
                              const SizedBox(height: 16),
                              const Text('No channels yet'),
                              const SizedBox(height: 4),
                              const Text('Pull down to refresh',
                                  style: TextStyle(fontSize: 12, color: Colors.grey)),
                              const SizedBox(height: 8),
                              FilledButton.tonal(
                                onPressed: _showCreateChannelDialog,
                                child: const Text('Create one'),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _refresh,
                  child: ListView.separated(
                    itemCount: chatProvider.channels.length,
                    separatorBuilder: (context, index) =>
                        const Divider(height: 1, indent: 72),
                    itemBuilder: (ctx, i) {
                      final ch = chatProvider.channels[i];
                      return ChannelTile(
                        channel: ch,
                        currentUserId: currentUserId,
                        unreadCount: chatProvider.unreadCount(ch.id),
                        userNames: chatProvider.userNames,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => ChatScreen(channel: ch),
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
      floatingActionButton: FloatingActionButton(
        heroTag: 'fab_channels',
        onPressed: _showCreateChannelDialog,
        tooltip: 'New Channel',
        child: const Icon(Icons.add),
      ),
    );
  }
}
