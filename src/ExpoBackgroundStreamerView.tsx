import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoBackgroundStreamerViewProps } from './ExpoBackgroundStreamer.types';

const NativeView: React.ComponentType<ExpoBackgroundStreamerViewProps> =
  requireNativeView('ExpoBackgroundStreamer');

export default function ExpoBackgroundStreamerView(props: ExpoBackgroundStreamerViewProps) {
  return <NativeView {...props} />;
}
