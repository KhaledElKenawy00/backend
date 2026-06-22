class UserModel {
  final int id;
  final String firstName;
  final String lastName;
  final String email;
  final String? phoneNumber;
  final String? accountStatus;
  final String role;
  final bool isEmailVerified;
  final bool isPhoneVerified;

  UserModel({
    required this.id,
    required this.firstName,
    required this.lastName,
    required this.email,
    this.phoneNumber,
    this.accountStatus,
    this.role = 'USER',
    this.isEmailVerified = false,
    this.isPhoneVerified = false,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id'] as int,
      firstName: json['firstName'] as String,
      lastName: json['lastName'] as String,
      email: json['email'] as String,
      phoneNumber: json['phoneNumber'] as String?,
      accountStatus: json['accountStatus'] as String?,
      role: json['role'] as String? ?? 'USER',
      isEmailVerified: json['isEmailVerified'] as bool? ?? false,
      isPhoneVerified: json['isPhoneVerified'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'firstName': firstName,
      'lastName': lastName,
      'email': email,
      'phoneNumber': phoneNumber,
      'accountStatus': accountStatus,
      'isEmailVerified': isEmailVerified,
      'isPhoneVerified': isPhoneVerified,
    };
  }

  String get fullName => '$firstName $lastName';
}
