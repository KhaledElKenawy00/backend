import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/room_model.dart';
import '../../models/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/room_provider.dart';
import '../../providers/workspace_provider.dart';
import '../../services/user_service.dart';
import 'create_room_screen.dart';
import 'room_call_screen.dart';

class RoomsScreen extends StatefulWidget {
  const RoomsScreen({super.key});

  @override
  State<RoomsScreen> createState() => _RoomsScreenState();
}

class _RoomsScreenState extends State<RoomsScreen> {
  int get _workspaceId =>
      context.read<WorkspaceProvider>().activeWorkspaceId ?? 1;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<RoomProvider>().loadRooms(_workspaceId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final roomProvider = context.watch<RoomProvider>();
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Rooms'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<RoomProvider>().loadRooms(_workspaceId),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final provider = context.read<RoomProvider>();
          final created = await Navigator.of(context).push<bool>(
            MaterialPageRoute(builder: (_) => const CreateRoomScreen()),
          );
          if (created == true) provider.loadRooms(_workspaceId);
        },
        icon: const Icon(Icons.add),
        label: const Text('New Room'),
      ),
      body: roomProvider.isLoading
          ? const Center(child: CircularProgressIndicator())
          : roomProvider.error != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(roomProvider.error!,
                          style: const TextStyle(color: Colors.red),
                          textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(
                          onPressed: () =>
                              context.read<RoomProvider>().loadRooms(_workspaceId),
                          child: const Text('Retry')),
                    ],
                  ),
                )
              : roomProvider.rooms.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.meeting_room_outlined,
                              size: 64,
                              color: colorScheme.onSurface.withValues(alpha: 0.3)),
                          const SizedBox(height: 12),
                          const Text('No rooms yet.\nCreate one to get started.',
                              textAlign: TextAlign.center),
                        ],
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: () =>
                          context.read<RoomProvider>().loadRooms(_workspaceId),
                      child: ListView.builder(
                        padding: const EdgeInsets.all(8),
                        itemCount: roomProvider.rooms.length,
                        itemBuilder: (ctx, i) {
                          final room = roomProvider.rooms[i];
                          return _RoomTile(room: room);
                        },
                      ),
                    ),
    );
  }
}

class _RoomTile extends StatelessWidget {
  final RoomModel room;
  const _RoomTile({required this.room});

  Future<void> _showEditDialog(BuildContext context) async {
    final nameCtrl = TextEditingController(text: room.name);
    final maxCtrl = TextEditingController(
        text: room.maxParticipants?.toString() ?? '');
    final provider = context.read<RoomProvider>();
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Room'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(labelText: 'Room name'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: maxCtrl,
              decoration: const InputDecoration(
                  labelText: 'Max participants (optional)'),
              keyboardType: TextInputType.number,
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Save')),
        ],
      ),
    );
    if (ok != true) return;
    final newName = nameCtrl.text.trim();
    final maxVal = int.tryParse(maxCtrl.text.trim());
    try {
      await provider.updateRoom(room.id,
          name: newName.isEmpty ? null : newName,
          maxParticipants: maxVal);
    } catch (e) {
      messenger.showSnackBar(
          SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }

  Future<void> _showMembersDialog(BuildContext context) async {
    final provider = context.read<RoomProvider>();
    final userService = context.read<UserService>();
    final messenger = ScaffoldMessenger.of(context);
    List<UserModel> allUsers = [];
    try {
      allUsers = await userService.getUsers();
    } catch (_) {}
    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          title: Text('Members of "${room.name}"'),
          content: SizedBox(
            width: double.maxFinite,
            child: allUsers.isEmpty
                ? const Text('No users found.')
                : ListView.builder(
                    shrinkWrap: true,
                    itemCount: allUsers.length,
                    itemBuilder: (_, i) {
                      final u = allUsers[i];
                      final isMember = provider
                          .rooms
                          .firstWhere((r) => r.id == room.id,
                              orElse: () => room)
                          .members
                          .contains(u.id);
                      return ListTile(
                        leading: CircleAvatar(
                            child: Text(u.fullName.isNotEmpty
                                ? u.fullName[0].toUpperCase()
                                : '?')),
                        title: Text(u.fullName),
                        subtitle: Text(u.email),
                        trailing: isMember
                            ? TextButton(
                                onPressed: () async {
                                  final sm = ScaffoldMessenger.of(ctx);
                                  try {
                                    await provider.removeMember(room.id, u.id);
                                    setState(() {});
                                  } catch (e) {
                                    sm.showSnackBar(SnackBar(
                                        content: Text(e
                                            .toString()
                                            .replaceFirst('Exception: ', ''))));
                                  }
                                },
                                child: const Text('Remove',
                                    style: TextStyle(color: Colors.red)),
                              )
                            : FilledButton(
                                onPressed: () async {
                                  final sm = ScaffoldMessenger.of(ctx);
                                  try {
                                    await provider.addMember(room.id, u.id);
                                    setState(() {});
                                  } catch (e) {
                                    sm.showSnackBar(SnackBar(
                                        content: Text(e
                                            .toString()
                                            .replaceFirst('Exception: ', ''))));
                                  }
                                },
                                child: const Text('Add'),
                              ),
                      );
                    },
                  ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Close')),
          ],
        ),
      ),
    );
    messenger.clearSnackBars();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final currentUserId = context.read<AuthProvider>().currentUser?.id;
    final isOwner = room.createdBy == currentUserId;

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: colorScheme.tertiaryContainer,
          child: Icon(Icons.meeting_room,
              color: colorScheme.onTertiaryContainer),
        ),
        title: Text(room.name,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(
          '${room.members.length} member${room.members.length == 1 ? '' : 's'}'
          '${room.maxParticipants != null ? ' · max ${room.maxParticipants}' : ''}',
          style: TextStyle(
              fontSize: 12,
              color: colorScheme.onSurface.withValues(alpha: 0.6)),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (isOwner) ...[
              IconButton(
                icon: const Icon(Icons.people_outline),
                tooltip: 'Members',
                onPressed: () => _showMembersDialog(context),
              ),
              IconButton(
                icon: const Icon(Icons.edit_outlined),
                tooltip: 'Edit',
                onPressed: () => _showEditDialog(context),
              ),
              IconButton(
                icon: const Icon(Icons.delete_outline, color: Colors.red),
                tooltip: 'Delete',
                onPressed: () async {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('Delete Room'),
                      content: Text('Delete "${room.name}"?'),
                      actions: [
                        TextButton(
                            onPressed: () => Navigator.pop(ctx, false),
                            child: const Text('Cancel')),
                        FilledButton(
                            style: FilledButton.styleFrom(
                                backgroundColor: Colors.red),
                            onPressed: () => Navigator.pop(ctx, true),
                            child: const Text('Delete')),
                      ],
                    ),
                  );
                  if (ok == true && context.mounted) {
                    context.read<RoomProvider>().deleteRoom(room.id);
                  }
                },
              ),
            ],
            FilledButton.icon(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => RoomCallScreen(roomId: room.id),
              )),
              icon: const Icon(Icons.call, size: 18),
              label: const Text('Join'),
            ),
          ],
        ),
      ),
    );
  }
}
