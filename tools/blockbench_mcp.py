"""Local Blockbench MCP client for model editing and inspection."""
import json
from pathlib import Path
import urllib.request


class Blockbench:
    def __init__(self, endpoint='http://127.0.0.1:3000/bb-mcp'):
        self.endpoint = endpoint
        self.session = None
        self.sequence = 0
        self.rpc('initialize', {'protocolVersion': '2024-11-05', 'capabilities': {},
                               'clientInfo': {'name': 'bigfatfish-workspace', 'version': '1.0'}})
        self.rpc('notifications/initialized', notification=True)

    def rpc(self, method, params=None, notification=False):
        self.sequence += 1
        body = {'jsonrpc': '2.0', 'method': method}
        if not notification:
            body['id'] = self.sequence
        if params is not None:
            body['params'] = params
        headers = {'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'}
        if self.session:
            headers['Mcp-Session-Id'] = self.session
        request = urllib.request.Request(self.endpoint, json.dumps(body).encode(), headers)
        with urllib.request.urlopen(request, timeout=60) as response:
            self.session = response.headers.get('Mcp-Session-Id', self.session)
            raw = response.read().decode('utf-8')
        result = json.loads(raw) if raw else {}
        if 'error' in result:
            raise RuntimeError(result['error'])
        return result.get('result', {})

    def call(self, tool_name, **arguments):
        result = self.rpc('tools/call', {'name': tool_name, 'arguments': arguments})
        if result.get('isError'):
            raise RuntimeError(result)
        return result

    def open_project(self, path):
        path = Path(path).resolve()
        content = path.read_text(encoding='utf-8')
        json.loads(content)
        self.call('risky_eval', code='window.bigfatfishImportChunks=[]; true')
        # Keep requests below the plugin's HTTP size limit. Escaping slash
        # literals avoids mistaking embedded PNG data for JavaScript comments.
        for start in range(0,len(content),128000):
            literal = json.dumps(content[start:start+128000]).replace('/', r'\u002f')
            self.call('risky_eval', code='window.bigfatfishImportChunks.push('+literal+'); true')
        file_info = json.dumps({'path': path.as_posix()}).replace('/', r'\u002f')
        return self.call('risky_eval', code=
            'Codecs.project.load(JSON.parse(window.bigfatfishImportChunks.join("")), '+file_info+'); '
            'delete window.bigfatfishImportChunks; '
            '({name:Project.name,meshes:Mesh.all.length,textures:Texture.all.length,groups:Group.all.length})')
