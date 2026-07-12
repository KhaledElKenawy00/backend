import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/workspace_model.dart';
import '../../providers/workspace_provider.dart';

class SkyDeskScreen extends StatefulWidget {
  final int workspaceId;
  const SkyDeskScreen({super.key, required this.workspaceId});

  @override
  State<SkyDeskScreen> createState() => _SkyDeskScreenState();
}

class _SkyDeskScreenState extends State<SkyDeskScreen> {
  bool _moveMode = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final p = context.read<WorkspaceProvider>();
      p.fetchDesks(widget.workspaceId);
      p.fetchMyDesk(widget.workspaceId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkspaceProvider>();
    final desks = provider.desks;
    final myDesk = provider.myDesk;

    final positioned = desks.where(_isPositioned).toList();
    final unpositioned = desks.where((d) => !_isPositioned(d)).toList();
    final onlineCount = desks.where((d) => d.status == 'ACTIVE').length;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Sky Desk'),
        actions: [
          if (myDesk != null)
            IconButton(
              icon: Icon(_moveMode ? Icons.done : Icons.open_with),
              tooltip: _moveMode ? 'Done moving' : 'Move my desk',
              onPressed: () => setState(() => _moveMode = !_moveMode),
            ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              context.read<WorkspaceProvider>().fetchDesks(widget.workspaceId);
            },
          ),
        ],
      ),
      body: Column(
        children: [
          _StatusBar(onlineCount: onlineCount, total: desks.length),
          Expanded(
            child: provider.isLoading && desks.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : _VirtualPlane(
                    desks: positioned,
                    myDesk: myDesk,
                    workspaceId: widget.workspaceId,
                    moveMode: _moveMode,
                  ),
          ),
          if (unpositioned.isNotEmpty)
            _UnpositionedBar(desks: unpositioned),
        ],
      ),
    );
  }

  static bool _isPositioned(DeskModel d) =>
      d.positionX != null &&
      d.positionY != null &&
      !(d.positionX == 0 && d.positionY == 0);
}

// ── Status bar ────────────────────────────────────────────────────────────────

class _StatusBar extends StatelessWidget {
  final int onlineCount;
  final int total;
  const _StatusBar({required this.onlineCount, required this.total});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: const BoxDecoration(
                color: Colors.green, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text('$onlineCount active · $total total',
              style: const TextStyle(fontSize: 12)),
        ],
      ),
    );
  }
}

// ── Virtual plane (InteractiveViewer) ─────────────────────────────────────────

class _VirtualPlane extends StatefulWidget {
  final List<DeskModel> desks;
  final DeskModel? myDesk;
  final int workspaceId;
  final bool moveMode;

  const _VirtualPlane({
    required this.desks,
    required this.myDesk,
    required this.workspaceId,
    required this.moveMode,
  });

  @override
  State<_VirtualPlane> createState() => _VirtualPlaneState();
}

class _VirtualPlaneState extends State<_VirtualPlane> {
  final TransformationController _tc = TransformationController();

  @override
  void dispose() {
    _tc.dispose();
    super.dispose();
  }

  void _onDeskDropped(DeskModel desk, Offset globalPos) {
    final RenderBox box = context.findRenderObject() as RenderBox;
    final local = box.globalToLocal(globalPos);
    final scene = _tc.toScene(local);
    final x = scene.dx.round().clamp(0, 2000);
    final y = scene.dy.round().clamp(0, 2000);
    context.read<WorkspaceProvider>().moveMyDesk(
        widget.workspaceId, desk.id, x, y);
  }

  @override
  Widget build(BuildContext context) {
    return InteractiveViewer(
      transformationController: _tc,
      boundaryMargin: const EdgeInsets.all(200),
      minScale: 0.3,
      maxScale: 3.0,
      scaleEnabled: !widget.moveMode,
      panEnabled: !widget.moveMode,
      child: SizedBox(
        width: 2000,
        height: 2000,
        child: Stack(
          children: [
            // Grid background
            CustomPaint(
              size: const Size(2000, 2000),
              painter: _GridPainter(),
            ),
            // Desks
            for (final desk in widget.desks)
              _DeskAvatar(
                desk: desk,
                draggable: widget.moveMode &&
                    widget.myDesk != null &&
                    desk.id == widget.myDesk!.id,
                onDropped: (pos) => _onDeskDropped(desk, pos),
              ),
          ],
        ),
      ),
    );
  }
}

// ── Desk avatar ───────────────────────────────────────────────────────────────

class _DeskAvatar extends StatelessWidget {
  final DeskModel desk;
  final bool draggable;
  final void Function(Offset globalPos) onDropped;

  const _DeskAvatar({
    required this.desk,
    required this.draggable,
    required this.onDropped,
  });

  Color _statusColor() => switch (desk.status) {
        'ACTIVE' => Colors.green,
        'AWAY' => Colors.orange,
        'DO_NOT_DISTURB' => Colors.red,
        'FOCUS_MODE' => Colors.purple,
        _ => Colors.grey,
      };

  String _displayName() => desk.fullName.isNotEmpty
      ? desk.fullName
      : (desk.workEmail ?? desk.nickName ?? 'Unknown');

  @override
  Widget build(BuildContext context) {
    final x = (desk.positionX ?? 100).toDouble();
    final y = (desk.positionY ?? 100).toDouble();

    final avatar = Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            CircleAvatar(
              radius: 24,
              backgroundColor:
                  Theme.of(context).colorScheme.primaryContainer,
              child: Text(
                _displayName()[0].toUpperCase(),
                style: TextStyle(
                    fontSize: 18,
                    color:
                        Theme.of(context).colorScheme.onPrimaryContainer,
                    fontWeight: FontWeight.bold),
              ),
            ),
            Positioned(
              right: -1,
              bottom: -1,
              child: Container(
                width: 14,
                height: 14,
                decoration: BoxDecoration(
                  color: _statusColor(),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 1.5),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
          decoration: BoxDecoration(
            color: Colors.black54,
            borderRadius: BorderRadius.circular(4),
          ),
          child: Text(
            _displayName(),
            style: const TextStyle(color: Colors.white, fontSize: 10),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );

    final positioned = Positioned(
      left: x - 28,
      top: y - 28,
      child: draggable
          ? Draggable<DeskModel>(
              data: desk,
              feedback: Opacity(opacity: 0.7, child: avatar),
              childWhenDragging: Opacity(opacity: 0.3, child: avatar),
              onDragEnd: (details) => onDropped(details.offset),
              child: avatar,
            )
          : avatar,
    );

    return positioned;
  }
}

// ── Unpositioned bar ──────────────────────────────────────────────────────────

class _UnpositionedBar extends StatelessWidget {
  final List<DeskModel> desks;
  const _UnpositionedBar({required this.desks});

  Color _statusColor(DeskModel d) => switch (d.status) {
        'ACTIVE' => Colors.green,
        'AWAY' => Colors.orange,
        'DO_NOT_DISTURB' => Colors.red,
        'FOCUS_MODE' => Colors.purple,
        _ => Colors.grey,
      };

  String _displayName(DeskModel d) => d.fullName.isNotEmpty
      ? d.fullName
      : (d.workEmail ?? d.nickName ?? 'Unknown');

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 72,
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 0, 0),
            child: Text('Not on map',
                style: TextStyle(
                    fontSize: 11,
                    color: Theme.of(context)
                        .colorScheme
                        .onSurface
                        .withValues(alpha: 0.5))),
          ),
          Expanded(
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              itemCount: desks.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (_, i) {
                final d = desks[i];
                return Tooltip(
                  message: _displayName(d),
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      CircleAvatar(
                        radius: 20,
                        backgroundColor:
                            Theme.of(context).colorScheme.secondaryContainer,
                        child: Text(
                          _displayName(d)[0].toUpperCase(),
                          style: TextStyle(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSecondaryContainer),
                        ),
                      ),
                      Positioned(
                        right: -1,
                        bottom: -1,
                        child: Container(
                          width: 10,
                          height: 10,
                          decoration: BoxDecoration(
                            color: _statusColor(d),
                            shape: BoxShape.circle,
                            border:
                                Border.all(color: Colors.white, width: 1.5),
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

// ── Grid painter ──────────────────────────────────────────────────────────────

class _GridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.grey.withValues(alpha: 0.12)
      ..strokeWidth = 1;
    const step = 80.0;
    for (double x = 0; x <= size.width; x += step) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (double y = 0; y <= size.height; y += step) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(_GridPainter _) => false;
}
