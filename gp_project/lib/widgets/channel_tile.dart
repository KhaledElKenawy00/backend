import 'package:flutter/material.dart';
import '../models/channel_model.dart';

class ChannelTile extends StatelessWidget {
  final ChannelModel channel;
  final int? currentUserId;
  final int unreadCount;
  final VoidCallback onTap;
  final Map<int, String>? userNames;

  const ChannelTile({
    super.key,
    required this.channel,
    this.currentUserId,
    this.unreadCount = 0,
    required this.onTap,
    this.userNames,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final displayName = channel.displayName(currentUserId, userNames: userNames);
    // First letter of name for DM avatar, '#' for channels
    final avatarLabel = channel.isDm
        ? (displayName.isNotEmpty ? displayName[0].toUpperCase() : '?')
        : '#';

    return ListTile(
      leading: CircleAvatar(
        backgroundColor: channel.isDm
            ? colorScheme.tertiaryContainer
            : colorScheme.primaryContainer,
        child: channel.isDm
            ? Text(
                avatarLabel,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: colorScheme.onTertiaryContainer,
                ),
              )
            : Icon(
                Icons.tag,
                color: colorScheme.onPrimaryContainer,
                size: 20,
              ),
      ),
      title: Text(
        displayName,
        style: TextStyle(
          fontWeight: unreadCount > 0 ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      subtitle: channel.isDm
          ? null
          : Text(
              'Group Channel',
              style: TextStyle(
                fontSize: 12,
                color: colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            ),
      trailing: unreadCount > 0
          ? Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: colorScheme.primary,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                unreadCount > 99 ? '99+' : '$unreadCount',
                style: TextStyle(
                  color: colorScheme.onPrimary,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              ),
            )
          : null,
      onTap: onTap,
    );
  }
}
