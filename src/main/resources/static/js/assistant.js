// Chat box for the local Ollama assistant. Replies stream in as plain text (shown with textContent, never as HTML).
(function () {
    const $ = id => document.getElementById(id);
    const log = $('aiLog'), input = $('aiInput'), send = $('aiSend'), explain = $('aiExplain');
    const state = $('aiState'), setup = $('aiSetup');
    if (!log) return;

    let ready = false, busy = false, model = '';

    function refreshButtons() {
        const off = !ready || busy;
        send.disabled = explain.disabled = off;
        document.querySelectorAll('.ai-actions [data-q]').forEach(b => b.disabled = off);
    }

    function addMessage(role, text) {
        const div = document.createElement('div');
        div.className = 'msg ' + role;
        div.textContent = text;
        log.appendChild(div);
        log.scrollTop = log.scrollHeight;
        return div;
    }

    async function checkStatus() {
        state.textContent = 'checking…'; state.className = 'pill';
        try {
            const s = await (await fetch('/api/assistant/status')).json();
            model = s.model;
            $('aiModel').textContent = s.model;
            if (!s.ollamaRunning) {
                ready = false; state.textContent = 'Ollama not running'; state.className = 'pill off';
                showSetup('Ollama is not running. Install it from https://ollama.com/download, start it, then run: ollama pull ' + s.model);
            } else if (!s.modelInstalled) {
                ready = false; state.textContent = 'model missing'; state.className = 'pill off';
                showSetup('Ollama is running but the model is not installed. Run this in a terminal: ollama pull ' + s.model);
            } else {
                ready = true; state.textContent = 'ready'; state.className = 'pill ok'; setup.hidden = true;
            }
        } catch (e) {
            ready = false; state.textContent = 'unavailable'; state.className = 'pill off';
            showSetup('Could not check the assistant status. Is the app still running?');
        }
        refreshButtons();
    }

    function showSetup(text) {
        setup.hidden = false;
        setup.textContent = text + ' ';
        const b = document.createElement('button');
        b.type = 'button'; b.className = 'btn'; b.textContent = 'Check again';
        b.onclick = checkStatus;
        setup.appendChild(b);
    }

    async function ask(url, body, userText) {
        if (busy || !ready) return;
        busy = true; refreshButtons();
        if (userText) addMessage('user', userText);
        const bubble = addMessage('bot busy', '');
        try {
            const res = await fetch(url, {
                method: 'POST',
                headers: body ? {'Content-Type': 'application/json'} : {},
                body: body ? JSON.stringify(body) : undefined
            });
            const reader = res.body.getReader(), decoder = new TextDecoder();
            for (;;) {
                const {done, value} = await reader.read();
                if (done) break;
                bubble.textContent += decoder.decode(value, {stream: true});
                log.scrollTop = log.scrollHeight;
            }
            if (!bubble.textContent.trim()) bubble.textContent = '(The model returned no text. Try again.)';
        } catch (e) {
            bubble.textContent = '⚠️ Lost the connection to the app. Try again.';
        } finally {
            bubble.classList.remove('busy');
            busy = false; refreshButtons();
        }
    }

    explain.onclick = () => ask('/api/assistant/summary', null, '✨ Explain my spending');
    document.querySelectorAll('.ai-actions [data-q]').forEach(b =>
        b.onclick = () => ask('/api/assistant/chat', {message: b.dataset.q}, b.dataset.q));
    $('aiForm').onsubmit = e => {
        e.preventDefault();
        const q = input.value.trim();
        if (!q) return;
        input.value = '';
        ask('/api/assistant/chat', {message: q}, q);
    };
    $('aiClear').onclick = async () => {
        await fetch('/api/assistant/reset', {method: 'POST'});
        log.textContent = '';
    };

    refreshButtons();
    checkStatus();
})();
