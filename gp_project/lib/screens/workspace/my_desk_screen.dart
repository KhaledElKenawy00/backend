import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/workspace_model.dart';
import '../../providers/workspace_provider.dart';

class MyDeskScreen extends StatefulWidget {
  final int workspaceId;
  const MyDeskScreen({super.key, required this.workspaceId});

  @override
  State<MyDeskScreen> createState() => _MyDeskScreenState();
}

class _MyDeskScreenState extends State<MyDeskScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<WorkspaceProvider>().fetchMyDesk(widget.workspaceId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkspaceProvider>();
    final desk = provider.myDesk;

    return Scaffold(
      appBar: AppBar(
        title: const Text('My Desk'),
        actions: [
          if (desk != null)
            IconButton(
              icon: const Icon(Icons.edit_outlined),
              tooltip: 'Edit Profile',
              onPressed: () => _showEditProfileDialog(context, desk),
            ),
        ],
      ),
      body: provider.isLoading && desk == null
          ? const Center(child: CircularProgressIndicator())
          : desk == null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.desk, size: 64, color: Colors.grey),
                      const SizedBox(height: 12),
                      const Text('No desk found for this workspace.'),
                      const SizedBox(height: 12),
                      FilledButton(
                        onPressed: () => context
                            .read<WorkspaceProvider>()
                            .fetchMyDesk(widget.workspaceId),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                )
              : _DeskProfileView(
                  desk: desk,
                  workspaceId: widget.workspaceId,
                ),
    );
  }

  Future<void> _showEditProfileDialog(BuildContext context, DeskModel desk) async {
    final fullNameCtrl = TextEditingController(text: desk.fullName);
    final nickNameCtrl = TextEditingController(text: desk.nickName ?? '');
    final titleCtrl = TextEditingController(text: desk.title ?? '');
    final bioCtrl = TextEditingController(text: desk.bio ?? '');
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Profile'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: fullNameCtrl,
                decoration: const InputDecoration(labelText: 'Full Name'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: nickNameCtrl,
                decoration: const InputDecoration(labelText: 'Nickname'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: titleCtrl,
                decoration: const InputDecoration(labelText: 'Job Title'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: bioCtrl,
                decoration: const InputDecoration(labelText: 'Bio'),
                maxLines: 3,
              ),
            ],
          ),
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
    try {
      await provider.updateMyDesk(
        widget.workspaceId,
        desk.id,
        fullName: fullNameCtrl.text.trim().isEmpty ? null : fullNameCtrl.text.trim(),
        nickName: nickNameCtrl.text.trim().isEmpty ? null : nickNameCtrl.text.trim(),
        title: titleCtrl.text.trim().isEmpty ? null : titleCtrl.text.trim(),
        bio: bioCtrl.text.trim().isEmpty ? null : bioCtrl.text.trim(),
      );
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }
}

class _DeskProfileView extends StatelessWidget {
  final DeskModel desk;
  final int workspaceId;
  const _DeskProfileView({required this.desk, required this.workspaceId});

  Color _statusColor() => switch (desk.status) {
        'ACTIVE' => Colors.green,
        'AWAY' => Colors.orange,
        'DO_NOT_DISTURB' => Colors.red,
        'FOCUS_MODE' => Colors.purple,
        _ => Colors.grey,
      };

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Avatar + name
        Center(
          child: Column(
            children: [
              CircleAvatar(
                radius: 40,
                backgroundColor: colorScheme.primaryContainer,
                child: Text(
                  desk.fullName.isNotEmpty
                      ? desk.fullName[0].toUpperCase()
                      : '?',
                  style: TextStyle(
                      fontSize: 32,
                      color: colorScheme.onPrimaryContainer,
                      fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(height: 12),
              Text(desk.fullName,
                  style: Theme.of(context).textTheme.headlineSmall
                      ?.copyWith(fontWeight: FontWeight.bold)),
              if (desk.nickName != null)
                Text('@${desk.nickName}',
                    style: TextStyle(
                        color: colorScheme.onSurface.withValues(alpha: 0.6))),
              if (desk.title != null) ...[
                const SizedBox(height: 4),
                Text(desk.title!,
                    style: TextStyle(
                        color: colorScheme.primary,
                        fontWeight: FontWeight.w500)),
              ],
            ],
          ),
        ),

        const SizedBox(height: 20),

        // Status card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                          color: _statusColor(), shape: BoxShape.circle),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '${desk.statusEmoji ?? ''} ${desk.status.replaceAll('_', ' ')}'
                          .trim(),
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const Spacer(),
                    TextButton(
                      onPressed: () => _showStatusDialog(context),
                      child: const Text('Change'),
                    ),
                  ],
                ),
                if (desk.statusCustomText != null && desk.statusCustomText!.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(desk.statusCustomText!,
                        style: TextStyle(
                            color:
                                colorScheme.onSurface.withValues(alpha: 0.6))),
                  ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Info card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _InfoRow(label: 'Role', value: desk.role),
                if (desk.workEmail != null)
                  _InfoRow(label: 'Email', value: desk.workEmail!),
                if (desk.phone != null)
                  _InfoRow(label: 'Phone', value: desk.phone!),
                if (desk.timezone != null)
                  _InfoRow(label: 'Timezone', value: desk.timezone!),
                if (desk.bio != null && desk.bio!.isNotEmpty)
                  _InfoRow(label: 'Bio', value: desk.bio!),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _showStatusDialog(BuildContext context) async {
    String selectedStatus = desk.status;
    final emojiCtrl =
        TextEditingController(text: desk.statusEmoji ?? '');
    final textCtrl =
        TextEditingController(text: desk.statusCustomText ?? '');
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          title: const Text('Update Status'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                initialValue: selectedStatus,
                decoration: const InputDecoration(labelText: 'Status'),
                items: const [
                  DropdownMenuItem(value: 'ACTIVE', child: Text('🟢 Active')),
                  DropdownMenuItem(value: 'AWAY', child: Text('🟡 Away')),
                  DropdownMenuItem(
                      value: 'DO_NOT_DISTURB', child: Text('🔴 Do Not Disturb')),
                  DropdownMenuItem(
                      value: 'FOCUS_MODE', child: Text('🟣 Focus Mode')),
                ],
                onChanged: (v) =>
                    setState(() => selectedStatus = v ?? 'ACTIVE'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: emojiCtrl,
                decoration:
                    const InputDecoration(labelText: 'Status emoji (optional)'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: textCtrl,
                decoration:
                    const InputDecoration(labelText: 'Status message (optional)'),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('Update')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    try {
      await provider.updateMyDeskStatus(
        workspaceId,
        desk.id,
        status: selectedStatus,
        statusEmoji:
            emojiCtrl.text.trim().isEmpty ? null : emojiCtrl.text.trim(),
        statusCustomText:
            textCtrl.text.trim().isEmpty ? null : textCtrl.text.trim(),
      );
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 80,
            child: Text(label,
                style: TextStyle(
                    color: Theme.of(context)
                        .colorScheme
                        .onSurface
                        .withValues(alpha: 0.6),
                    fontSize: 12)),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}
