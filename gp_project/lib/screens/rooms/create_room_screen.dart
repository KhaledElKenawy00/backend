import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/room_provider.dart';

class CreateRoomScreen extends StatefulWidget {
  const CreateRoomScreen({super.key});

  @override
  State<CreateRoomScreen> createState() => _CreateRoomScreenState();
}

class _CreateRoomScreenState extends State<CreateRoomScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _maxController = TextEditingController();
  bool _loading = false;

  @override
  void dispose() {
    _nameController.dispose();
    _maxController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      final max = _maxController.text.trim().isNotEmpty
          ? int.tryParse(_maxController.text.trim())
          : null;
      await context.read<RoomProvider>().createRoom(
            name: _nameController.text.trim(),
            workspaceId: 1,
            maxParticipants: max,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', '')),
          backgroundColor: Colors.red));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create Room')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _nameController,
                  decoration: const InputDecoration(
                    labelText: 'Room Name',
                    prefixIcon: Icon(Icons.meeting_room_outlined),
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) =>
                      v == null || v.trim().isEmpty ? 'Enter a room name' : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _maxController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Max Participants (optional)',
                    prefixIcon: Icon(Icons.group_outlined),
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) {
                    if (v == null || v.trim().isEmpty) return null;
                    final n = int.tryParse(v.trim());
                    if (n == null || n < 2) return 'Must be at least 2';
                    return null;
                  },
                ),
                const SizedBox(height: 32),
                FilledButton(
                  onPressed: _loading ? null : _submit,
                  style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14)),
                  child: _loading
                      ? const SizedBox(
                          height: 20, width: 20,
                          child: CircularProgressIndicator(
                              strokeWidth: 2, color: Colors.white))
                      : const Text('Create Room',
                          style: TextStyle(fontSize: 16)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
