class ParticipantModel {
  final int userId;
  final bool muted;
  final bool cameraOn;
  final bool screenSharing;
  final String? joinedAt;

  ParticipantModel({
    required this.userId,
    this.muted = true,
    this.cameraOn = false,
    this.screenSharing = false,
    this.joinedAt,
  });

  factory ParticipantModel.fromJson(Map<String, dynamic> json) =>
      ParticipantModel(
        userId: json['userId'] as int? ?? 0,
        muted: json['muted'] as bool? ?? true,
        cameraOn: json['cameraOn'] as bool? ?? false,
        screenSharing: json['screenSharing'] as bool? ?? false,
        joinedAt: json['joinedAt'] as String?,
      );

  ParticipantModel copyWith({bool? muted, bool? cameraOn, bool? screenSharing}) =>
      ParticipantModel(
        userId: userId,
        muted: muted ?? this.muted,
        cameraOn: cameraOn ?? this.cameraOn,
        screenSharing: screenSharing ?? this.screenSharing,
        joinedAt: joinedAt,
      );
}

class RoomModel {
  final String id;
  final int workspaceId;
  final String name;
  final String? channelId;
  final String agoraChannelName;
  final List<int> members;
  final int? maxParticipants;
  final int? createdBy;
  final String? createdAt;
  final String? updatedAt;

  RoomModel({
    required this.id,
    required this.workspaceId,
    required this.name,
    this.channelId,
    required this.agoraChannelName,
    this.members = const [],
    this.maxParticipants,
    this.createdBy,
    this.createdAt,
    this.updatedAt,
  });

  factory RoomModel.fromJson(Map<String, dynamic> json) {
    final raw = json['members'];
    final membersList =
        raw is List ? raw.map((e) => e as int).toList() : <int>[];
    return RoomModel(
      id: json['id']?.toString() ?? '',
      workspaceId: json['workspaceId'] as int? ?? 0,
      name: json['name'] as String? ?? '',
      channelId: json['channelId'] as String?,
      agoraChannelName: json['agoraChannelName'] as String? ?? '',
      members: membersList,
      maxParticipants: json['maxParticipants'] as int?,
      createdBy: json['createdBy'] as int?,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
    );
  }
}

class JoinRoomResponse {
  final RoomModel room;
  final String agoraChannelName;
  final String agoraToken;
  final List<ParticipantModel> participants;

  JoinRoomResponse({
    required this.room,
    required this.agoraChannelName,
    this.agoraToken = '',
    required this.participants,
  });

  factory JoinRoomResponse.fromJson(Map<String, dynamic> json) =>
      JoinRoomResponse(
        room: RoomModel.fromJson(json['room'] as Map<String, dynamic>),
        agoraChannelName: json['agoraChannelName'] as String? ?? '',
        agoraToken: json['agoraToken'] as String? ?? '',
        participants: (json['participants'] as List<dynamic>? ?? [])
            .map((e) => ParticipantModel.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
