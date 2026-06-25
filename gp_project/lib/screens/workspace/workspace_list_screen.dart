import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/workspace_model.dart';
import '../../providers/workspace_provider.dart';
import 'workspace_detail_screen.dart';

class WorkspaceListScreen extends StatefulWidget {
  const WorkspaceListScreen({super.key});

  @override
  State<WorkspaceListScreen> createState() => _WorkspaceListScreenState();
}

class _WorkspaceListScreenState extends State<WorkspaceListScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<WorkspaceProvider>().fetchMyWorkspaces();
    });
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkspaceProvider>();
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Workspaces'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<WorkspaceProvider>().fetchMyWorkspaces(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateDialog(context),
        icon: const Icon(Icons.add),
        label: const Text('New Workspace'),
      ),
      body: provider.isLoading
          ? const Center(child: CircularProgressIndicator())
          : provider.error != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(provider.error!,
                          style: const TextStyle(color: Colors.red),
                          textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(
                          onPressed: () =>
                              context.read<WorkspaceProvider>().fetchMyWorkspaces(),
                          child: const Text('Retry')),
                    ],
                  ),
                )
              : provider.workspaces.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.business_outlined,
                              size: 64,
                              color: colorScheme.onSurface.withValues(alpha: 0.3)),
                          const SizedBox(height: 12),
                          const Text('No workspaces yet.\nCreate one to get started.',
                              textAlign: TextAlign.center),
                          const SizedBox(height: 16),
                          FilledButton.icon(
                            onPressed: () => _showCreateDialog(context),
                            icon: const Icon(Icons.add),
                            label: const Text('Create Workspace'),
                          ),
                        ],
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: () =>
                          context.read<WorkspaceProvider>().fetchMyWorkspaces(),
                      child: ListView.builder(
                        padding: const EdgeInsets.all(12),
                        itemCount: provider.workspaces.length,
                        itemBuilder: (ctx, i) {
                          final ws = provider.workspaces[i];
                          final isActive =
                              provider.activeWorkspace?.id == ws.id;
                          return _WorkspaceTile(
                            workspace: ws,
                            isActive: isActive,
                          );
                        },
                      ),
                    ),
    );
  }

  Future<void> _showCreateDialog(BuildContext context) async {
    final nameCtrl = TextEditingController();
    final slugCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final provider = context.read<WorkspaceProvider>();
    final messenger = ScaffoldMessenger.of(context);

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Create Workspace'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(labelText: 'Name *'),
              textCapitalization: TextCapitalization.words,
            ),
            const SizedBox(height: 8),
            TextField(
              controller: slugCtrl,
              decoration: const InputDecoration(
                  labelText: 'Slug *',
                  hintText: 'my-workspace'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: descCtrl,
              decoration: const InputDecoration(labelText: 'Description'),
              maxLines: 2,
            ),
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
      await provider.createWorkspace(
        name: nameCtrl.text.trim(),
        slug: slugCtrl.text.trim(),
        description: descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim(),
      );
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', ''))));
    }
  }
}

class _WorkspaceTile extends StatelessWidget {
  final WorkspaceModel workspace;
  final bool isActive;

  const _WorkspaceTile({required this.workspace, required this.isActive});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      color: isActive ? colorScheme.primaryContainer : null,
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: colorScheme.primary,
          child: Text(
            workspace.name.isNotEmpty ? workspace.name[0].toUpperCase() : 'W',
            style: TextStyle(color: colorScheme.onPrimary, fontWeight: FontWeight.bold),
          ),
        ),
        title: Text(workspace.name,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(
          workspace.description ?? workspace.slug,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
              fontSize: 12,
              color: colorScheme.onSurface.withValues(alpha: 0.6)),
        ),
        trailing: isActive
            ? Icon(Icons.check_circle, color: colorScheme.primary)
            : const Icon(Icons.arrow_forward_ios, size: 16),
        onTap: () {
          context.read<WorkspaceProvider>().selectWorkspace(workspace);
          Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => WorkspaceDetailScreen(workspace: workspace),
          ));
        },
      ),
    );
  }
}
