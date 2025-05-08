import { registerWebModule, NativeModule } from 'expo';

import { ExpoBackgroundStreamerModuleEvents } from './ExpoBackgroundStreamer.types';

class ExpoBackgroundStreamerModule extends NativeModule<ExpoBackgroundStreamerModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(ExpoBackgroundStreamerModule, 'ExpoBackgroundStreamerModule');
