import 'dart:async';
import 'package:agora_rtc_engine/agora_rtc_engine.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/room_provider.dart';

const String _agoraAppId = '5db80389fc284a3a8c166979882f118d';
// Replace with fresh token from Agora Console every hour
const String _agoraFallbackToken = '007eJxTYBA7uDztuuFv2689oSYxH93/bwusNva6UyVs9nriXceAnngFBtOUJAsDYwvLtGQjC5NE40SLZEMzM0tzSwsLozRDQ4sUg59BWQ2BjAy5kYUMjFAI4vMwpOWUlpSkFsUXZealMzAAAHi+IqQ=';
const String _agoraFallbackChannel = 'flutter_ring';

class RoomCallScreen extends StatefulWidget {
  final String roomId;
  const RoomCallScreen({super.key, required this.roomId});

  @override
  State<RoomCallScreen> createState() => _RoomCallScreenState();
}

class _RoomCallScreenState extends State<RoomCallScreen> {
  RtcEngine? _engine;
  RoomProvider? _roomProvider;
  bool _muted = true;
  bool _cameraOn = false;
  bool _joined = false;
  String? _agoraError;
  int? _localUid;
  String? _channelId;
  final Set<int> _remoteUids = {};

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _roomProvider ??= context.read<RoomProvider>();
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final provider = context.read<RoomProvider>();
      final userId = context.read<AuthProvider>().currentUser?.id ?? 0;
      await provider.initWebSocket();
      await provider.joinRoom(widget.roomId);
      final channelName = provider.agoraChannelName ?? widget.roomId;
      final token = provider.agoraToken;
      await _initAgora(channelName, token, userId);
    });
  }

  Future<void> _initAgora(String channelName, String? agoraToken, int uid) async {
    await [Permission.microphone, Permission.camera].request();

    _channelId = channelName;
    _localUid = uid;

    _engine = createAgoraRtcEngine();
    await _engine!.initialize(const RtcEngineContext(appId: _agoraAppId));
    await _engine!.enableVideo();

    _engine!.registerEventHandler(RtcEngineEventHandler(
      onJoinChannelSuccess: (connection, elapsed) async {
        await _engine?.setEnableSpeakerphone(true);
        if (mounted) {
          setState(() {
            _joined = true;
            _localUid = connection.localUid;
          });
        }
      },
      onUserJoined: (connection, remoteUid, elapsed) {
        if (mounted) { setState(() => _remoteUids.add(remoteUid)); }
      },
      onUserOffline: (connection, remoteUid, reason) {
        if (mounted) { setState(() => _remoteUids.remove(remoteUid)); }
      },
      onError: (err, msg) {
        if (mounted) { setState(() => _agoraError = '${err.name} (${err.index}): $msg'); }
      },
    ));

    await _engine!.joinChannel(
      token: _agoraFallbackToken,
      channelId: _agoraFallbackChannel,
      uid: uid,
      options: const ChannelMediaOptions(
        channelProfile: ChannelProfileType.channelProfileCommunication,
        clientRoleType: ClientRoleType.clientRoleBroadcaster,
      ),
    );
  }

  void _toggleMute() {
    setState(() => _muted = !_muted);
    _engine?.muteLocalAudioStream(_muted);
    context.read<RoomProvider>().sendStateUpdate(
        muted: _muted, cameraOn: _cameraOn, screenSharing: false);
  }

  void _toggleCamera() {
    setState(() => _cameraOn = !_cameraOn);
    _engine?.muteLocalVideoStream(!_cameraOn);
    if (_cameraOn) _engine?.startPreview();
    context.read<RoomProvider>().sendStateUpdate(
        muted: _muted, cameraOn: _cameraOn, screenSharing: false);
  }

  Future<void> _leave() async {
    final provider = context.read<RoomProvider>();
    final navigator = Navigator.of(context);
    await _engine?.leaveChannel();
    await _engine?.release();
    _engine = null;
    await provider.leaveRoom();
    if (mounted) navigator.pop();
  }

  @override
  void dispose() {
    _engine?.leaveChannel();
    _engine?.release();
    _roomProvider?.leaveRoom();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final roomProvider = context.watch<RoomProvider>();
    final room = roomProvider.activeRoom;

    if (roomProvider.isLoading && room == null) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(color: Colors.white),
              SizedBox(height: 16),
              Text('Joining room…',
                  style: TextStyle(color: Colors.white, fontSize: 16)),
            ],
          ),
        ),
      );
    }

    final allUids = [_localUid, ..._remoteUids].whereType<int>().toList();

    return Scaffold(
      backgroundColor: const Color(0xFF1A1A2E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(room?.name ?? 'Room',
                style: const TextStyle(color: Colors.white)),
            Text(
              '${allUids.length} participant${allUids.length == 1 ? '' : 's'}',
              style: const TextStyle(color: Colors.white70, fontSize: 12),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: _agoraError != null
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.error_outline,
                            color: Colors.redAccent, size: 48),
                        const SizedBox(height: 12),
                        Text(_agoraError!,
                            style: const TextStyle(
                                color: Colors.redAccent, fontSize: 13),
                            textAlign: TextAlign.center),
                      ],
                    ),
                  )
                : !_joined || _engine == null
                ? const Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircularProgressIndicator(color: Colors.white54),
                        SizedBox(height: 12),
                        Text('Connecting to call…',
                            style: TextStyle(color: Colors.white54)),
                      ],
                    ),
                  )
                : allUids.isEmpty
                    ? const Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.people_outline,
                                size: 64, color: Colors.white30),
                            SizedBox(height: 12),
                            Text('Waiting for others…',
                                style: TextStyle(color: Colors.white54)),
                          ],
                        ),
                      )
                    : GridView.builder(
                        padding: const EdgeInsets.all(16),
                        gridDelegate:
                            const SliverGridDelegateWithMaxCrossAxisExtent(
                          maxCrossAxisExtent: 200,
                          mainAxisSpacing: 12,
                          crossAxisSpacing: 12,
                        ),
                        itemCount: allUids.length,
                        itemBuilder: (ctx, i) {
                          final uid = allUids[i];
                          final isLocal = uid == _localUid;
                          return _VideoTile(
                            engine: _engine!,
                            uid: uid,
                            channelId: _channelId ?? '',
                            isLocal: isLocal,
                            cameraOn: isLocal ? _cameraOn : true,
                            muted: isLocal ? _muted : false,
                          );
                        },
                      ),
          ),
          Container(
            color: Colors.black45,
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _ControlButton(
                  icon: _muted ? Icons.mic_off : Icons.mic,
                  label: _muted ? 'Unmute' : 'Mute',
                  color: _muted ? Colors.red : Colors.white,
                  onTap: _toggleMute,
                ),
                _ControlButton(
                  icon: _cameraOn ? Icons.videocam : Icons.videocam_off,
                  label: _cameraOn ? 'Cam Off' : 'Cam On',
                  color: _cameraOn ? Colors.blue : Colors.white,
                  onTap: _toggleCamera,
                ),
                _ControlButton(
                  icon: Icons.call_end,
                  label: 'Leave',
                  color: Colors.red,
                  onTap: _leave,
                  filled: true,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _VideoTile extends StatelessWidget {
  final RtcEngine engine;
  final int uid;
  final String channelId;
  final bool isLocal;
  final bool cameraOn;
  final bool muted;

  const _VideoTile({
    required this.engine,
    required this.uid,
    required this.channelId,
    required this.isLocal,
    required this.cameraOn,
    required this.muted,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white10,
        borderRadius: BorderRadius.circular(16),
        border: isLocal ? Border.all(color: Colors.blue, width: 2) : null,
      ),
      clipBehavior: Clip.hardEdge,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (cameraOn)
            isLocal
                ? AgoraVideoView(
                    controller: VideoViewController(
                      rtcEngine: engine,
                      canvas: const VideoCanvas(uid: 0),
                    ),
                  )
                : AgoraVideoView(
                    controller: VideoViewController.remote(
                      rtcEngine: engine,
                      canvas: VideoCanvas(uid: uid),
                      connection: RtcConnection(channelId: channelId),
                    ),
                  )
          else
            Center(
              child: CircleAvatar(
                radius: 32,
                backgroundColor: Colors.white24,
                child: Text(
                  '$uid',
                  style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 16),
                ),
              ),
            ),
          Positioned(
            bottom: 8,
            left: 8,
            right: 8,
            child: Row(
              children: [
                Icon(muted ? Icons.mic_off : Icons.mic,
                    size: 14,
                    color: muted ? Colors.red : Colors.green),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    isLocal ? 'You' : 'User $uid',
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        shadows: [Shadow(blurRadius: 4)]),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ControlButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  final bool filled;

  const _ControlButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
    this.filled = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircleAvatar(
            radius: 28,
            backgroundColor: filled ? color : Colors.white12,
            child: Icon(icon,
                color: filled ? Colors.white : color, size: 26),
          ),
          const SizedBox(height: 6),
          Text(label,
              style: TextStyle(
                  color: color == Colors.white ? Colors.white70 : color,
                  fontSize: 12)),
        ],
      ),
    );
  }
}
