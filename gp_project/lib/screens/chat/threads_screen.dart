import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/channel_model.dart';
import '../../models/thread_model.dart';
import '../../providers/auth_provider.dart';
import '../../services/thread_service.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/auth_storage.dart';
import 'thread_chat_screen.dart';

class ThreadsScreen extends StatefulWidget {
  final ChannelModel channel;
  const ThreadsScreen({super.key, required this.channel});

  @override
  State<ThreadsScreen> createState() => _ThreadsScreenState();
}

class _ThreadsScreenState extends State<ThreadsScreen> {
  late final ThreadService _threadService;
  List<ThreadModel> _threads = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    final apiClient = ApiClient(AuthStorage());
    _threadService = ThreadService(apiClient);
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      _threads = await _threadService.getThreads(widget.channel.id);
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createThread() async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New Thread'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
              labelText: 'Thread name', border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, controller.text.trim()),
              child: const Text('Create')),
        ],
      ),
    );
    if (name == null || name.isEmpty) return;
    try {
      final thread = await _threadService.createThread(widget.channel.id, name);
      setState(() => _threads = [thread, ..._threads]);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString().replaceFirst('Exception: ', '')),
              backgroundColor: Colors.red));
    }
  }

  Future<void> _deleteThread(ThreadModel thread) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Thread'),
        content: Text('Delete "${thread.name}"?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Colors.red),
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await _threadService.deleteThread(thread.id);
      setState(() => _threads = _threads.where((t) => t.id != thread.id).toList());
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString().replaceFirst('Exception: ', '')),
              backgroundColor: Colors.red));
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final currentUserId = context.read<AuthProvider>().currentUser?.id;

    return Scaffold(
      appBar: AppBar(
        title: Text('Threads — ${widget.channel.name.isEmpty ? 'Channel' : widget.channel.name}'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _createThread,
        child: const Icon(Icons.add),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
              : _threads.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.forum_outlined, size: 64,
                              color: colorScheme.onSurface.withValues(alpha: 0.3)),
                          const SizedBox(height: 12),
                          const Text('No threads yet.\nTap + to start one.',
                              textAlign: TextAlign.center),
                        ],
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.separated(
                        padding: const EdgeInsets.all(8),
                        itemCount: _threads.length,
                        separatorBuilder: (context, index) => const Divider(height: 1),
                        itemBuilder: (ctx, i) {
                          final t = _threads[i];
                          return ListTile(
                            leading: CircleAvatar(
                              backgroundColor: colorScheme.primaryContainer,
                              child: Icon(Icons.forum,
                                  color: colorScheme.onPrimaryContainer, size: 20),
                            ),
                            title: Text(t.name,
                                style: const TextStyle(fontWeight: FontWeight.w500)),
                            subtitle: Text('by user ${t.createdBy ?? '?'}',
                                style: TextStyle(
                                    fontSize: 12,
                                    color: colorScheme.onSurface.withValues(alpha: 0.6))),
                            trailing: t.createdBy == currentUserId
                                ? IconButton(
                                    icon: const Icon(Icons.delete_outline, color: Colors.red),
                                    onPressed: () => _deleteThread(t))
                                : null,
                            onTap: () => Navigator.of(context).push(MaterialPageRoute(
                              builder: (_) => ThreadChatScreen(thread: t),
                            )),
                          );
                        },
                      ),
                    ),
    );
  }
}
