import 'package:flutter/material.dart';

import '../models/action_step.dart';

IconData stepIcon(StepType t) => switch (t) {
      StepType.tap => Icons.touch_app,
      StepType.doubleTap => Icons.ads_click,
      StepType.longPress => Icons.pan_tool_alt,
      StepType.areaTap => Icons.highlight_alt,
      StepType.swipe => Icons.swipe,
      StepType.wait => Icons.hourglass_bottom,
    };
