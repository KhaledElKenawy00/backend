import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../providers/notification_provider.dart';
import '../../providers/workspace_provider.dart';
import '../../providers/room_provider.dart';
import '../chat/channels_screen.dart';
import '../chat/dms_screen.dart';
import '../notifications/notifications_screen.dart';
import '../profile/profile_screen.dart';
import '../rooms/rooms_screen.dart';
import '../workspace/workspace_list_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _initProviders();
    // Poll every 10 s so newly-added rooms/channels appear without manual refresh
    _pollTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      if (!mounted) return;
      _silentRefresh();
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  void _silentRefresh() {
    final workspaceId =
        context.read<WorkspaceProvider>().activeWorkspaceId ?? 1;
    context.read<ChatProvider>().loadChannels(workspaceId, silent: true);
    context.read<RoomProvider>().loadRooms(workspaceId, silent: true);
  }

  void _onTabSelected(int index) {
    setState(() => _currentIndex = index);
    final workspaceId =
        context.read<WorkspaceProvider>().activeWorkspaceId ?? 1;
    // Refresh rooms/channels each time the user taps the tab
    if (index == 0) {
      context.read<ChatProvider>().loadChannels(workspaceId);
    } else if (index == 2) {
      context.read<RoomProvider>().loadRooms(workspaceId);
    }
  }

  Future<void> _initProviders() async {
    final authProvider = context.read<AuthProvider>();
    final chatProvider = context.read<ChatProvider>();
    final notifProvider = context.read<NotificationProvider>();
    final workspaceProvider = context.read<WorkspaceProvider>();
    final roomProvider = context.read<RoomProvider>();

    final role = await authProvider.authStorage.getUserRole() ?? 'USER';
    final userId = authProvider.currentUser?.id ?? 0;

    await workspaceProvider.fetchMyWorkspaces();

    final workspaceId = workspaceProvider.activeWorkspaceId ?? 1;

    await chatProvider.initWebSocket(userId, role);
    await notifProvider.initWebSocket();
    notifProvider.onMembershipUpdated = (type, wsId) {
      if (!mounted) return;
      final wsProvider = context.read<WorkspaceProvider>();
      final activeId = wsProvider.activeWorkspaceId ?? 1;
      final targetId = wsId != 0 ? wsId : activeId;
      if (wsId != 0 && wsId != activeId) {
        final ws = wsProvider.workspaces
            .where((w) => w.id == wsId)
            .firstOrNull;
        if (ws != null) wsProvider.selectWorkspace(ws);
      }
      context.read<ChatProvider>().loadChannels(targetId, silent: true);
      context.read<RoomProvider>().loadRooms(targetId, silent: true);
    };
    await Future.wait([
      chatProvider.loadChannels(workspaceId),
      chatProvider.loadDms(),
      chatProvider.loadUserNames(),
      notifProvider.load(),
      roomProvider.loadRooms(workspaceId),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final notifProvider = context.watch<NotificationProvider>();
    final chatProvider = context.watch<ChatProvider>();

    final totalChatUnread = chatProvider.channels
        .fold(0, (sum, ch) => sum + chatProvider.unreadCount(ch.id));

    final screens = [
      const ChannelsScreen(),
      const DmsScreen(),
      const RoomsScreen(),
      const WorkspaceListScreen(),
      const NotificationsScreen(),
      const ProfileScreen(),
    ];

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: screens,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: _onTabSelected,
        destinations: [
          NavigationDestination(
            icon: Badge(
              isLabelVisible: totalChatUnread > 0,
              label: Text(totalChatUnread > 99 ? '99+' : '$totalChatUnread'),
              child: const Icon(Icons.tag),
            ),
            selectedIcon: const Icon(Icons.tag),
            label: 'Channels',
          ),
          const NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline),
            selectedIcon: Icon(Icons.chat_bubble),
            label: 'DMs',
          ),
          const NavigationDestination(
            icon: Icon(Icons.mic_none),
            selectedIcon: Icon(Icons.mic),
            label: 'Rooms',
          ),
          const NavigationDestination(
            icon: Icon(Icons.business_outlined),
            selectedIcon: Icon(Icons.business),
            label: 'Workspace',
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: notifProvider.unreadCount > 0,
              label: Text(notifProvider.unreadCount > 99
                  ? '99+'
                  : '${notifProvider.unreadCount}'),
              child: const Icon(Icons.notifications_outlined),
            ),
            selectedIcon: const Icon(Icons.notifications),
            label: 'Notifications',
          ),
          const NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}
