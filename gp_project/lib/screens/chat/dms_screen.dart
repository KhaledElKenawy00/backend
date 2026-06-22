import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../services/user_service.dart';
import '../../widgets/channel_tile.dart';
import 'chat_screen.dart';

class DmsScreen extends StatefulWidget {
  const DmsScreen({super.key});

  @override
  State<DmsScreen> createState() => _DmsScreenState();
}

class _DmsScreenState extends State<DmsScreen> {
  Future<void> _refresh() async {
    await context.read<ChatProvider>().loadDms();
  }

  Future<void> _showNewDmDialog() async {
    final authProvider = context.read<AuthProvider>();
    final userService = UserService(authProvider.apiClient);
    final currentUserId = authProvider.currentUser?.id;

    List<UserModel> allUsers = [];
    bool loadingUsers = true;
    String? loadError;
    String searchQuery = '';

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) {
          if (loadingUsers && allUsers.isEmpty && loadError == null) {
            userService.getUsers().then((users) {
              setDialogState(() {
                allUsers = users.where((u) => u.id != currentUserId).toList();
                loadingUsers = false;
              });
            }).catchError((e) {
              setDialogState(() {
                loadError = e.toString().replaceFirst('Exception: ', '');
                loadingUsers = false;
              });
            });
          }

          final filtered = searchQuery.isEmpty
              ? allUsers
              : allUsers
                  .where((u) =>
                      u.fullName.toLowerCase().contains(searchQuery.toLowerCase()) ||
                      u.email.toLowerCase().contains(searchQuery.toLowerCase()))
                  .toList();

          return AlertDialog(
            title: const Text('New Direct Message'),
            content: SizedBox(
              width: double.maxFinite,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (!loadingUsers && loadError == null && allUsers.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: TextField(
                        autofocus: true,
                        decoration: const InputDecoration(
                          hintText: 'Search by name or email…',
                          prefixIcon: Icon(Icons.search),
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                        onChanged: (v) => setDialogState(() => searchQuery = v),
                      ),
                    ),
                  if (loadingUsers)
                    const Padding(
                      padding: EdgeInsets.all(24),
                      child: CircularProgressIndicator(),
                    )
                  else if (loadError != null)
                    Text(loadError!, style: const TextStyle(color: Colors.red))
                  else if (filtered.isEmpty)
                    const Text('No users found')
                  else
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 220),
                      child: ListView.separated(
                        shrinkWrap: true,
                        itemCount: filtered.length,
                        separatorBuilder: (_, _) => const Divider(height: 1),
                        itemBuilder: (_, i) {
                          final u = filtered[i];
                          return ListTile(
                            leading: CircleAvatar(
                              child: Text(u.firstName[0].toUpperCase()),
                            ),
                            title: Text(u.fullName),
                            subtitle: Text(
                              u.email,
                              style: const TextStyle(fontSize: 12),
                            ),
                            onTap: () async {
                              Navigator.pop(ctx);
                              try {
                                final chatProvider = context.read<ChatProvider>();
                                final channel = await chatProvider.createOrGetDm(u.id);
                                if (!mounted) return;
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => ChatScreen(channel: channel),
                                  ),
                                );
                                chatProvider.loadDms();
                              } catch (e) {
                                if (!mounted) return;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(e.toString().replaceFirst('Exception: ', '')),
                                    backgroundColor: Colors.red,
                                  ),
                                );
                              }
                            },
                          );
                        },
                      ),
                    ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Cancel'),
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
        title: const Text('Direct Messages'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
        ],
      ),
      body: chatProvider.isLoadingChannels
          ? const Center(child: CircularProgressIndicator())
          : chatProvider.dms.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.chat_bubble_outline,
                        size: 64,
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.3),
                      ),
                      const SizedBox(height: 16),
                      const Text('No direct messages yet'),
                      const SizedBox(height: 8),
                      FilledButton.tonal(
                        onPressed: _showNewDmDialog,
                        child: const Text('Start a conversation'),
                      ),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _refresh,
                  child: ListView.separated(
                    itemCount: chatProvider.dms.length,
                    separatorBuilder: (context, index) =>
                        const Divider(height: 1, indent: 72),
                    itemBuilder: (ctx, i) {
                      final dm = chatProvider.dms[i];
                      return ChannelTile(
                        channel: dm,
                        currentUserId: currentUserId,
                        unreadCount: chatProvider.unreadCount(dm.id),
                        userNames: chatProvider.userNames,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => ChatScreen(channel: dm),
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
      floatingActionButton: FloatingActionButton(
        heroTag: 'fab_dms',
        onPressed: _showNewDmDialog,
        tooltip: 'New DM',
        child: const Icon(Icons.edit),
      ),
    );
  }
}
