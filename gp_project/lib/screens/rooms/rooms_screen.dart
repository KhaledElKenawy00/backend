import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/room_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/room_provider.dart';
import 'create_room_screen.dart';
import 'room_call_screen.dart';

class RoomsScreen extends StatefulWidget {
  const RoomsScreen({super.key});

  @override
  State<RoomsScreen> createState() => _RoomsScreenState();
}

class _RoomsScreenState extends State<RoomsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<RoomProvider>().loadRooms(1);
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
            onPressed: () => context.read<RoomProvider>().loadRooms(1),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final provider = context.read<RoomProvider>();
          final created = await Navigator.of(context).push<bool>(
            MaterialPageRoute(builder: (_) => const CreateRoomScreen()),
          );
          if (created == true) provider.loadRooms(1);
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
                              context.read<RoomProvider>().loadRooms(1),
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
                          context.read<RoomProvider>().loadRooms(1),
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
            if (isOwner)
              IconButton(
                icon: const Icon(Icons.delete_outline, color: Colors.red),
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
