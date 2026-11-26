import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// 숫자 입력 필드. 외부 값이 바뀌면 (입력 중이 아닐 때) 표시도 따라간다.
class NumField extends StatefulWidget {
  const NumField({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    this.decimal = false,
    this.min = 0,
    this.max,
    this.helper,
    this.width = 160,
  });

  final String label;
  final num value;
  final ValueChanged<num> onChanged;
  final bool decimal;
  final num min;
  final num? max;
  final String? helper;
  final double width;

  @override
  State<NumField> createState() => _NumFieldState();
}

class _NumFieldState extends State<NumField> {
  late final TextEditingController _c =
      TextEditingController(text: _fmt(widget.value));
  final _focus = FocusNode();

  String _fmt(num v) => widget.decimal
      ? v.toStringAsFixed(1).replaceAll(RegExp(r'\.0$'), '')
      : v.toInt().toString();

  @override
  void didUpdateWidget(NumField old) {
    super.didUpdateWidget(old);
    if (!_focus.hasFocus && _parse(_c.text) != widget.value) {
      _c.text = _fmt(widget.value);
    }
  }

  num? _parse(String s) => widget.decimal ? double.tryParse(s) : int.tryParse(s);

  @override
  void dispose() {
    _c.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: widget.width,
      child: TextField(
        controller: _c,
        focusNode: _focus,
        keyboardType: TextInputType.numberWithOptions(decimal: widget.decimal),
        inputFormatters: [
          FilteringTextInputFormatter.allow(
              RegExp(widget.decimal ? r'[0-9.]' : r'[0-9]')),
        ],
        decoration: InputDecoration(
          labelText: widget.label,
          helperText: widget.helper,
          isDense: true,
          border: const OutlineInputBorder(),
        ),
        onChanged: (s) {
          var v = _parse(s);
          if (v == null) return;
          if (v < widget.min) v = widget.min;
          if (widget.max != null && v > widget.max!) v = widget.max!;
          widget.onChanged(v);
        },
        onEditingComplete: () {
          _c.text = _fmt(widget.value);
          _focus.unfocus();
        },
      ),
    );
  }
}
