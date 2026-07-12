import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/workspace_model.dart';
import '../../providers/workspace_provider.dart';
import 'my_desk_screen.dart';

class WorkspaceDetailScreen extends StatefulWidget {
  final WorkspaceModel workspace;
  const WorkspaceDetailScreen({super.key, required this.workspace});

  @override
  State<WorkspaceDetailScreen> createState() => _WorkspaceDetailScreenState();
}

class _WorkspaceDetailScreenState extends State<WorkspaceDetailScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final p = context.read<WorkspaceProvider>();
      final id = widget.workspace.id;
      p.fetchDesks(id);
      p.fetchMyDesk(id);
      p.fetchTeams(id);
      p.fetchInvitations(id);
    });
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.workspace.name),
        actions: [
          IconButton(
            icon: const Icon(Icons.person_outline),
            tooltip: 'My Desk',
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => MyDeskScreen(workspaceId: widget.workspace.id),
            )),
          ),
        ],
        bottom: TabBar(
          controller: _tabs,
          tabs: const [
            Tab(icon: Icon(Icons.people), text: 'Members'),
            Tab(icon: Icon(Icons.group_work), text: 'Teams'),
            Tab(icon: Icon(Icons.mail_outline), text: 'Invitations'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _MembersTab(workspaceId: widget.workspace.id),
          _TeamsTab(workspaceId: widget.workspace.id),
          _InvitationsTab(workspaceId: widget.workspace.id),
        ],
      ),
    );
  }
}

// ── Members Tab ───────────────────────────────────────────────────────────────

class _MembersTab extends StatelessWidget {
  final int workspaceId;
  const _MembersTab({required this.workspaceId});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkspaceProvider>();
    if (provider.isLoading && provider.desks.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (provider.desks.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('No members yet.'),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () => context.read<WorkspaceProvider>().fetchDesks(workspaceId),
              child: const Text('Refresh'),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () => context.read<WorkspaceProvider>().fetchDesks(workspaceId),
      child: ListView.builder(
        padding: const EdgeInsets.all(8),
        itemCount: provider.desks.length,
        itemBuilder: (ctx, i) => _DeskTile(desk: provider.desks[i]),
      ),
    );
  }
}

class _DeskTile extends StatelessWidget {
  final DeskModel desk;
  const _DeskTile({required this.desk});

  String _displayName() => desk.fullName.isNotEmpty
      ? desk.fullName
      : (desk.workEmail ?? desk.nickName ?? 'Unknown');

  Color _statusColor() => switch (desk.status) {
        'ACTIVE' => Colors.green,
        'AWAY' => Colors.orange,
        'DO_NOT_DISTURB' => Colors.red,
        'FOCUS_MODE' => Colors.purple,
        _ => Colors.grey,
      };

  String _statusLabel() => switch (desk.status) {
        'ACTIVE' => 'Active',
        'AWAY' => 'Away',
        'DO_NOT_DISTURB' => 'Do Not Disturb',
        'FOCUS_MODE' => 'Focus Mode',
        _ => desk.status,
      };

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.only(bottom: 6),
      child: ListTile(
        leading: Stack(
          clipBehavior: Clip.none,
          children: [
            CircleAvatar(
              backgroundColor: colorScheme.secondaryContainer,
              child: Text(
                _displayName()[0].toUpperCase(),
                style: TextStyle(color: colorScheme.onSecondaryContainer),
              ),
            ),
            Positioned(
              right: -2,
              bottom: -2,
              child: Container(
                width: 12,
                height: 12,
                decoration: BoxDecoration(
                  color: _statusColor(),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 1.5),
                ),
              ),
            ),
          ],
        ),
        title: Text(_displayName(),
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (desk.title != null)
              Text(desk.title!, style: const TextStyle(fontSize: 12)),
            Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                      color: _statusColor(), shape: BoxShape.circle),
                ),
                const SizedBox(width: 4),
                Text(
                  '${desk.statusEmoji ?? ''} ${_statusLabel()}'
                      '${desk.statusCustomText != null ? ' · ${desk.statusCustomText}' : ''}'
                      .trim(),
                  style: TextStyle(
                      fontSize: 11,
                      color: colorScheme.onSurface.withValues(alpha: 0.6)),
                ),
              ],
            ),
          ],
        ),
        trailing: Chip(
          label: Text(desk.role, style: const TextStyle(fontSize: 10)),
          padding: EdgeInsets.zero,
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }
}

// ── Teams Tab ─────────────────────────────────────────────────────────────────

class _TeamsTab extends StatelessWidget {
  final int workspaceId;
  const _TeamsTab({required this.workspaceId});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkspaceProvider>();
    final role = provider.myDesk?.role ?? '';
    final isAdmin = role == 'ADMIN' || role == 'OWNER';
    final body = provider.isLoading && provider.teams.isEmpty
        ? const Center(child: CircularProgressIndicator())
        : provider.teams.isEmpty
            ? const Center(child: Text('No teams yet.'))
            : RefreshIndicator(
                onRefresh: () =>
                    context.read<WorkspaceProvider>().fetchTeams(workspaceId),
                child: ListView.builder(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 80),
                  itemCount: provider.teams.length,
                  itemBuilder: (ctx, i) => _TeamTile(
                      team: provider.teams[i],
                      workspaceId: workspaceId,
                      isAdmin: isAdmin),
                ),
              );
    return Stack(
      children: [
        body,
        if (isAdmin)
          Positioned(
            right: 16,
            bottom: 16,
            child: FloatingActionButton(
              heroTag: 'teams_fab',
              mini: true,
              onPressed: () => _showCreateTeamDialog(context),
              child: const Icon(Icons.add),
            ),
          ),
      ],
    );
  }

  Future<void> _showCreateTeamDialog(BuildContext context) async {
    final nameCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Create Team'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: 'Team name *')),
            const SizedBox(height: 8),
            TextField(
                controller: descCtrl,
                decoration: const InputDecoration(labelText: 'Description'),
                maxLines: 2),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Create')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await provider.createTeam(workspaceId, nameCtrl.text.trim(),
          description:
              descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim());
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }
}

class _TeamTile extends StatelessWidget {
  final TeamModel team;
  final int workspaceId;
  final bool isAdmin;
  const _TeamTile({required this.team, required this.workspaceId, required this.isAdmin});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 6),
      child: ListTile(
        leading: const CircleAvatar(child: Icon(Icons.group)),
        title: Text(team.name,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: team.description != null ? Text(team.description!) : null,
        trailing: isAdmin
            ? Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    icon: const Icon(Icons.edit_outlined),
                    onPressed: () => _showEditDialog(context),
                  ),
                  IconButton(
                    icon: const Icon(Icons.delete_outline, color: Colors.red),
                    onPressed: () => _confirmDelete(context),
                  ),
                ],
              )
            : null,
      ),
    );
  }

  Future<void> _showEditDialog(BuildContext context) async {
    final nameCtrl = TextEditingController(text: team.name);
    final descCtrl = TextEditingController(text: team.description ?? '');
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Team'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: 'Team name')),
            const SizedBox(height: 8),
            TextField(
                controller: descCtrl,
                decoration: const InputDecoration(labelText: 'Description'),
                maxLines: 2),
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
    try {
      await provider.updateTeam(workspaceId, team.id,
          name: nameCtrl.text.trim().isEmpty ? null : nameCtrl.text.trim(),
          description:
              descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim());
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }

  Future<void> _confirmDelete(BuildContext context) async {
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Team'),
        content: Text('Delete "${team.name}"?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Colors.red),
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await provider.deleteTeam(workspaceId, team.id);
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }
}

// ── Invitations Tab ───────────────────────────────────────────────────────────

class _InvitationsTab extends StatelessWidget {
  final int workspaceId;
  const _InvitationsTab({required this.workspaceId});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkspaceProvider>();
    final body = provider.isLoading && provider.invitations.isEmpty
        ? const Center(child: CircularProgressIndicator())
        : provider.invitations.isEmpty
            ? const Center(child: Text('No pending invitations.'))
            : RefreshIndicator(
                onRefresh: () => context
                    .read<WorkspaceProvider>()
                    .fetchInvitations(workspaceId),
                child: ListView.builder(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 80),
                  itemCount: provider.invitations.length,
                  itemBuilder: (ctx, i) => _InvitationTile(
                      invitation: provider.invitations[i],
                      workspaceId: workspaceId),
                ),
              );
    return Stack(
      children: [
        body,
        Positioned(
          right: 16,
          bottom: 16,
          child: FloatingActionButton(
            heroTag: 'invitations_fab',
            mini: true,
            onPressed: () => _showInviteDialog(context),
            child: const Icon(Icons.person_add),
          ),
        ),
      ],
    );
  }

  Future<void> _showInviteDialog(BuildContext context) async {
    final emailCtrl = TextEditingController();
    String selectedRole = 'MEMBER';
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          title: const Text('Invite Member'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: emailCtrl,
                decoration: const InputDecoration(labelText: 'Email address *'),
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: selectedRole,
                decoration: const InputDecoration(labelText: 'Role'),
                items: const [
                  DropdownMenuItem(value: 'GUEST', child: Text('Guest')),
                  DropdownMenuItem(value: 'MEMBER', child: Text('Member')),
                  DropdownMenuItem(value: 'ADMIN', child: Text('Admin')),
                ],
                onChanged: (v) => setState(() => selectedRole = v ?? 'MEMBER'),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('Invite')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    try {
      final inv = await provider.inviteMember(
          workspaceId, emailCtrl.text.trim(), selectedRole);
      if (inv.token != null && context.mounted) {
        _showTokenDialog(context, inv.token!);
      } else {
        messenger.showSnackBar(
            const SnackBar(content: Text('Invitation sent!')));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }

  void _showTokenDialog(BuildContext context, String token) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Invitation Token'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Share this token with the invitee:',
                style: TextStyle(fontSize: 13)),
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(8),
              ),
              child: SelectableText(
                token,
                style: const TextStyle(
                    fontFamily: 'monospace', fontSize: 12),
              ),
            ),
            const SizedBox(height: 8),
            const Text('Token expires in 7 days.',
                style: TextStyle(fontSize: 11, color: Colors.grey)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
            },
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

class _InvitationTile extends StatelessWidget {
  final InvitationModel invitation;
  final int workspaceId;
  const _InvitationTile({required this.invitation, required this.workspaceId});

  Color _statusColor() => switch (invitation.status) {
        'ACCEPTED' => Colors.green,
        'DECLINED' => Colors.red,
        _ => Colors.orange,
      };

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 6),
      child: ListTile(
        leading: const CircleAvatar(child: Icon(Icons.mail_outline)),
        title: Text(invitation.invitedEmail),
        subtitle: Row(
          children: [
            Chip(
              label: Text(invitation.role, style: const TextStyle(fontSize: 10)),
              padding: EdgeInsets.zero,
              visualDensity: VisualDensity.compact,
            ),
            const SizedBox(width: 8),
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                  color: _statusColor(), shape: BoxShape.circle),
            ),
            const SizedBox(width: 4),
            Text(invitation.status,
                style: TextStyle(
                    fontSize: 11, color: _statusColor())),
          ],
        ),
        trailing: invitation.status == 'PENDING'
            ? Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (invitation.token != null)
                    IconButton(
                      icon: const Icon(Icons.key_outlined),
                      tooltip: 'Show token',
                      onPressed: () => showDialog(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          title: const Text('Invitation Token'),
                          content: SelectableText(
                            invitation.token!,
                            style: const TextStyle(
                                fontFamily: 'monospace', fontSize: 12),
                          ),
                          actions: [
                            TextButton(
                                onPressed: () => Navigator.pop(ctx),
                                child: const Text('Close')),
                          ],
                        ),
                      ),
                    ),
                  IconButton(
                    icon: const Icon(Icons.cancel_outlined, color: Colors.red),
                    tooltip: 'Revoke',
                    onPressed: () => _revoke(context),
                  ),
                ],
              )
            : null,
      ),
    );
  }

  Future<void> _revoke(BuildContext context) async {
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);
    try {
      await provider.revokeInvitation(workspaceId, invitation.id);
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }
}
