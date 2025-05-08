import * as React from 'react';

import { ExpoBackgroundStreamerViewProps } from './ExpoBackgroundStreamer.types';

export default function ExpoBackgroundStreamerView(props: ExpoBackgroundStreamerViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
