import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import '../../providers/auth_provider.dart';
import '../../services/user_service.dart';
import '../auth/login_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Uint8List? _photoBytes;
  bool _loadingPhoto = false;
  late final UserService _userService;

  @override
  void initState() {
    super.initState();
    final apiClient = context.read<AuthProvider>().apiClient;
    _userService = UserService(apiClient);
    _loadPhoto();
  }

  Future<void> _loadPhoto() async {
    setState(() => _loadingPhoto = true);
    try {
      final bytes = await _userService.getPhoto();
      if (mounted) setState(() => _photoBytes = bytes);
    } catch (_) {
    } finally {
      if (mounted) setState(() => _loadingPhoto = false);
    }
  }

  Future<void> _pickAndUploadPhoto() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 512,
      maxHeight: 512,
      imageQuality: 85,
    );
    if (picked == null) return;

    setState(() => _loadingPhoto = true);
    try {
      await _userService.uploadPhoto(picked.path);
      await _loadPhoto();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تم تحديث الصورة بنجاح ✓')),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _loadingPhoto = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(e.toString().replaceFirst('Exception: ', '')),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  void _showChangePasswordDialog() {
    final oldCtrl = TextEditingController();
    final newCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    final formKey = GlobalKey<FormState>();
    bool obscureOld = true;
    bool obscureNew = true;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('Change Password'),
          content: Form(
            key: formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  controller: oldCtrl,
                  obscureText: obscureOld,
                  decoration: InputDecoration(
                    labelText: 'Current Password',
                    border: const OutlineInputBorder(),
                    suffixIcon: IconButton(
                      icon: Icon(obscureOld
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined),
                      onPressed: () =>
                          setDialogState(() => obscureOld = !obscureOld),
                    ),
                  ),
                  validator: (v) =>
                      (v == null || v.isEmpty) ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: newCtrl,
                  obscureText: obscureNew,
                  decoration: InputDecoration(
                    labelText: 'New Password',
                    border: const OutlineInputBorder(),
                    suffixIcon: IconButton(
                      icon: Icon(obscureNew
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined),
                      onPressed: () =>
                          setDialogState(() => obscureNew = !obscureNew),
                    ),
                  ),
                  validator: (v) {
                    if (v == null || v.isEmpty) return 'Required';
                    if (v.length < 6) return 'Minimum 6 characters';
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: confirmCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Confirm New Password',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) {
                    if (v == null || v.isEmpty) return 'Required';
                    if (v != newCtrl.text) return 'Passwords do not match';
                    return null;
                  },
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                if (!formKey.currentState!.validate()) return;
                Navigator.pop(ctx);
                try {
                  await _userService.changePassword(
                    oldCtrl.text,
                    newCtrl.text,
                  );
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                          content: Text('Password changed successfully')),
                    );
                  }
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(e.toString()),
                        backgroundColor: Colors.red,
                      ),
                    );
                  }
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _logout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Sign Out'),
        content: const Text('Are you sure you want to sign out?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Sign Out'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;
    if (!mounted) return;

    await context.read<AuthProvider>().logout();
    if (!mounted) return;

    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();
    final user = authProvider.currentUser;
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => authProvider.refreshProfile(),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            const SizedBox(height: 16),
            GestureDetector(
              onTap: _pickAndUploadPhoto,
              child: Stack(
                children: [
                  CircleAvatar(
                    radius: 56,
                    backgroundColor: colorScheme.primaryContainer,
                    backgroundImage: _photoBytes != null
                        ? MemoryImage(_photoBytes!)
                        : null,
                    child: _loadingPhoto
                        ? const CircularProgressIndicator()
                        : _photoBytes == null
                            ? Text(
                                user != null
                                    ? user.firstName[0].toUpperCase()
                                    : '?',
                                style: TextStyle(
                                  fontSize: 36,
                                  color: colorScheme.onPrimaryContainer,
                                  fontWeight: FontWeight.bold,
                                ),
                              )
                            : null,
                  ),
                  Positioned(
                    bottom: 0,
                    right: 0,
                    child: Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: colorScheme.primary,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        Icons.camera_alt,
                        size: 16,
                        color: colorScheme.onPrimary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            if (user != null) ...[
              Text(
                user.fullName,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                user.email,
                style: TextStyle(
                  color: colorScheme.onSurface.withValues(alpha: 0.6),
                ),
              ),
            ],
            const SizedBox(height: 32),
            Card(
              child: Column(
                children: [
                  if (user != null) ...[
                    _InfoTile(
                      icon: Icons.person_outline,
                      label: 'Full Name',
                      value: user.fullName,
                    ),
                    const Divider(height: 1, indent: 56),
                    _CopyTile(
                      icon: Icons.badge_outlined,
                      label: 'User ID',
                      value: user.id.toString(),
                    ),
                    const Divider(height: 1, indent: 56),
                    _InfoTile(
                      icon: Icons.email_outlined,
                      label: 'Email',
                      value: user.email,
                    ),
                    if (user.phoneNumber != null) ...[
                      const Divider(height: 1, indent: 56),
                      _InfoTile(
                        icon: Icons.phone_outlined,
                        label: 'Phone',
                        value: user.phoneNumber!,
                      ),
                    ],
                    const Divider(height: 1, indent: 56),
                    _InfoTile(
                      icon: Icons.shield_outlined,
                      label: 'Role',
                      value: user.role,
                    ),
                    if (user.accountStatus != null) ...[
                      const Divider(height: 1, indent: 56),
                      _InfoTile(
                        icon: Icons.info_outline,
                        label: 'Status',
                        value: user.accountStatus!,
                      ),
                    ],
                    const Divider(height: 1, indent: 56),
                    _InfoTile(
                      icon: Icons.verified_outlined,
                      label: 'Email Verified',
                      value: user.isEmailVerified ? 'Yes' : 'No',
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.lock_outline),
                    title: const Text('Change Password'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: _showChangePasswordDialog,
                  ),
                  const Divider(height: 1, indent: 56),
                  ListTile(
                    leading:
                        const Icon(Icons.logout, color: Colors.red),
                    title: const Text('Sign Out',
                        style: TextStyle(color: Colors.red)),
                    onTap: _logout,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _InfoTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _InfoTile({
    required this.icon,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return ListTile(
      leading: Icon(icon, color: colorScheme.primary),
      title: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          color: colorScheme.onSurface.withValues(alpha: 0.6),
        ),
      ),
      subtitle: Text(
        value,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
      ),
    );
  }
}

class _CopyTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _CopyTile({
    required this.icon,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return ListTile(
      leading: Icon(icon, color: colorScheme.primary),
      title: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          color: colorScheme.onSurface.withValues(alpha: 0.6),
        ),
      ),
      subtitle: Text(
        value,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
      ),
      trailing: IconButton(
        icon: const Icon(Icons.copy, size: 18),
        tooltip: 'Copy',
        onPressed: () {
          Clipboard.setData(ClipboardData(text: value));
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('$label copied'),
              duration: const Duration(seconds: 1),
            ),
          );
        },
      ),
    );
  }
}
