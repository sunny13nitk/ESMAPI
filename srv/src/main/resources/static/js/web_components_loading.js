const scripts = {
    'lxpWebComponents': {
    src: '/web-components.js',
    loaded: false
    }
};

function load(...scripts) {
    return Promise.all(scripts.map((script) => loadScript(script)));
}

function loadScript(name) {
    return new Promise((resolve, reject) => {
    // Resolve if already loaded
    if (scripts[name].loaded) {
        resolve({ script: name, loaded: true, status: 'Already Loaded' });
    } else {
        // Load script
        const script = document.createElement('script');
        script.type = 'text/javascript';
        // TODO: uncomment for local testing while distensions are not configured. Remove after destinations configuring.
        // script.src = 'https://support.learning-test.sap.com/web-components.js';
        script.src = scripts[name].src;
        if (script.readyState) {
        // IE
        script.onreadystatechange = () => {
            if (
            script.readyState === 'loaded' ||
            script.readyState === 'complete'
            ) {
            script.onreadystatechange = null;
            scripts[name].loaded = true;
            resolve({ script: name, loaded: true, status: 'Loaded' });
            }
        };
        } else {
        // Others
        script.onload = () => {
            scripts[name].loaded = true;
            resolve({ script: name, loaded: true, status: 'Loaded' });
        };
        }
        script.onerror = (error) =>
        resolve({ script: name, loaded: false, status: 'Not loaded' });
        document.body.appendChild(script);
    }
    });
}

load('lxpWebComponents').then((data) => {
    if (data.loaded) {
    console.log('LXP Web-components were successfully loaded.');
    } else {
    console.log('LXP Web-components are not loaded.');
    }
})
.catch((error) => console.error(error))