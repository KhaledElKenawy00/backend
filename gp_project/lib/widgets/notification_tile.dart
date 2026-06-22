import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/notification_model.dart';

class NotificationTile extends StatelessWidget {
  final NotificationModel notification;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  const NotificationTile({
    super.key,
    required this.notification,
    required this.onTap,
    required this.onDelete,
  });

  String _formatTime(String isoString) {
    try {
      final dt = DateTime.parse(isoString).toLocal();
      final now = DateTime.now();
      final diff = now.difference(dt);
      if (diff.inMinutes < 1) return 'Just now';
      if (diff.inHours < 1) return '${diff.inMinutes}m ago';
      if (diff.inDays < 1) return '${diff.inHours}h ago';
      if (diff.inDays < 7) return '${diff.inDays}d ago';
      return DateFormat('MMM d').format(dt);
    } catch (_) {
      return '';
    }
  }

  IconData _iconForType(String? type) {
    switch (type) {
      case 'MESSAGE':
        return Icons.chat_bubble_outline;
      case 'MENTION':
        return Icons.alternate_email;
      case 'SYSTEM':
        return Icons.info_outline;
      default:
        return Icons.notifications_outlined;
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isUnread = !notification.read;

    return Dismissible(
      key: Key('notif_${notification.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: Colors.red,
        child: const Icon(Icons.delete, color: Colors.white),
      ),
      onDismissed: (_) => onDelete(),
      child: ListTile(
        onTap: onTap,
        tileColor: isUnread
            ? colorScheme.primaryContainer.withValues(alpha: 0.25)
            : null,
        leading: Stack(
          clipBehavior: Clip.none,
          children: [
            CircleAvatar(
              backgroundColor: isUnread
                  ? colorScheme.primaryContainer
                  : colorScheme.surfaceContainerHighest,
              child: Icon(
                _iconForType(notification.type),
                color: isUnread
                    ? colorScheme.onPrimaryContainer
                    : colorScheme.onSurface,
                size: 20,
              ),
            ),
            if (isUnread)
              Positioned(
                top: -2,
                right: -2,
                child: Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    color: colorScheme.primary,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
          ],
        ),
        title: Text(
          notification.title,
          style: TextStyle(
            fontWeight: isUnread ? FontWeight.bold : FontWeight.normal,
          ),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              notification.body,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 13,
                color: colorScheme.onSurface.withValues(alpha: 0.7),
              ),
            ),
            const SizedBox(height: 2),
            Text(
              _formatTime(notification.createdAt),
              style: TextStyle(
                fontSize: 11,
                color: colorScheme.onSurface.withValues(alpha: 0.5),
              ),
            ),
          ],
        ),
        isThreeLine: true,
      ),
    );
  }
}
