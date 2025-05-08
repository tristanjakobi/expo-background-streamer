import { NativeModule, requireNativeModule } from 'expo';

import { ExpoBackgroundStreamerModuleEvents } from './ExpoBackgroundStreamer.types';

declare class ExpoBackgroundStreamerModule extends NativeModule<ExpoBackgroundStreamerModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoBackgroundStreamerModule>('ExpoBackgroundStreamer');
