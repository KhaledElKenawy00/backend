import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../providers/notification_provider.dart';
import '../chat/channels_screen.dart';
import '../chat/dms_screen.dart';
import '../notifications/notifications_screen.dart';
import '../profile/profile_screen.dart';
import '../rooms/rooms_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;

  @override
  void initState() {
    super.initState();
    _initProviders();
  }

  Future<void> _initProviders() async {
    final authProvider = context.read<AuthProvider>();
    final chatProvider = context.read<ChatProvider>();
    final notifProvider = context.read<NotificationProvider>();

    final role = await authProvider.authStorage.getUserRole() ?? 'USER';
    // Use currentUser.id (from getMe) — storage may hold stale "0" from pre-fix logins
    final userId = authProvider.currentUser?.id ?? 0;

    await chatProvider.initWebSocket(userId, role);
    await notifProvider.initWebSocket();
    await Future.wait([
      chatProvider.loadChannels(1),
      chatProvider.loadDms(),
      chatProvider.loadUserNames(),
      notifProvider.load(),
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
        onDestinationSelected: (i) => setState(() => _currentIndex = i),
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
