class MessageModel {
  final String id;
  final String channelId;
  final int senderId;
  final String content;
  final String? type;
  final String? threadId;
  final String? replyToId;
  final List<int> mentions;
  final String? clientMessageId;
  final bool deleted;
  final String? deletedAt;
  final String createdAt;
  final String? updatedAt;

  MessageModel({
    required this.id,
    required this.channelId,
    required this.senderId,
    required this.content,
    this.type,
    this.threadId,
    this.replyToId,
    this.mentions = const [],
    this.clientMessageId,
    this.deleted = false,
    this.deletedAt,
    required this.createdAt,
    this.updatedAt,
  });

  factory MessageModel.fromJson(Map<String, dynamic> json) {
    final rawMentions = json['mentions'];
    List<int> mentionsList = [];
    if (rawMentions is List) {
      mentionsList = rawMentions.map((e) => e as int).toList();
    }
    return MessageModel(
      id: json['id']?.toString() ?? '',
      channelId: json['channelId']?.toString() ?? '',
      senderId: json['senderId'] as int,
      content: json['content'] as String? ?? '',
      type: json['type'] as String?,
      threadId: json['threadId']?.toString(),
      replyToId: json['replyToId']?.toString(),
      mentions: mentionsList,
      clientMessageId: json['clientMessageId'] as String?,
      deleted: json['deleted'] as bool? ?? false,
      deletedAt: json['deletedAt'] as String?,
      createdAt: json['createdAt'] as String? ?? '',
      updatedAt: json['updatedAt'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'channelId': channelId,
      'senderId': senderId,
      'content': content,
      'type': type,
      'threadId': threadId,
      'replyToId': replyToId,
      'mentions': mentions,
      'clientMessageId': clientMessageId,
      'deleted': deleted,
      'deletedAt': deletedAt,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }

  MessageModel copyWith({
    String? id,
    String? channelId,
    int? senderId,
    String? content,
    String? type,
    String? threadId,
    String? replyToId,
    List<int>? mentions,
    String? clientMessageId,
    bool? deleted,
    String? deletedAt,
    String? createdAt,
    String? updatedAt,
  }) {
    return MessageModel(
      id: id ?? this.id,
      channelId: channelId ?? this.channelId,
      senderId: senderId ?? this.senderId,
      content: content ?? this.content,
      type: type ?? this.type,
      threadId: threadId ?? this.threadId,
      replyToId: replyToId ?? this.replyToId,
      mentions: mentions ?? this.mentions,
      clientMessageId: clientMessageId ?? this.clientMessageId,
      deleted: deleted ?? this.deleted,
      deletedAt: deletedAt ?? this.deletedAt,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}
