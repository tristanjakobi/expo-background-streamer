const { withPlugins } = require("@expo/config-plugins");

const withExpoBackgroundStreamer = (config) => {
  return withPlugins(config, [
    // Add any necessary plugin configurations here
  ]);
};

module.exports = withExpoBackgroundStreamer;
