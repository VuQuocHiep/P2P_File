const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = path.resolve(__dirname, '../..');
const assets = path.join(source, 'common/src/main/resources/META-INF/resources/assets');

/** Execute the same ES modules as the browser, without rewriting imports or installing a bundler. */
function createModuleLoader(globals = {}) {
  const context = vm.createContext({
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    AbortController,
    FormData,
    ...globals,
  });
  const cache = new Map();
  function getModule(filename) {
    if (!cache.has(filename)) {
      cache.set(
        filename,
        new vm.SourceTextModule(fs.readFileSync(filename, 'utf8'), {
          context,
          identifier: filename,
        }),
      );
    }
    return cache.get(filename);
  }
  return async function load(relative) {
    const module = getModule(path.resolve(source, relative));
    if (module.status === 'unlinked') {
      await module.link((specifier, parent) =>
        getModule(
          specifier.startsWith('/assets/')
            ? path.join(assets, specifier.slice('/assets/'.length))
            : path.resolve(path.dirname(parent.identifier), specifier),
        ),
      );
    }
    if (module.status === 'linked') await module.evaluate();
    return module.namespace;
  };
}

module.exports = { createModuleLoader, source };
