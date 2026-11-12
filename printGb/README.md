# Gabryelle Print Agent

Agente desktop Java para Windows que fica na bandeja do sistema e recebe trabalhos de impressão do Gabryelle Prime por WebSocket local. O servidor só escuta em `127.0.0.1:38182`; cada conexão também precisa vir de uma origem permitida e autenticar com o token de pareamento mostrado na tela de configuração.

## Requisitos

- Windows 10/11
- JDK 25 ou superior
- IntelliJ IDEA com suporte a Maven
- Impressoras instaladas no Windows e reconhecidas pelo `javax.print`

## Abrir e executar no IntelliJ IDEA

1. Abra a pasta `printGb` no IntelliJ e escolha **Open as Project**.
2. Aceite a importação do projeto Maven e aguarde o download das dependências.
3. Configure um Project SDK 25 ou superior em **File > Project Structure > Project**.
4. Execute `com.gabryelle.agent.Main` em `src/main/java`.
5. O agente inicia minimizado na bandeja. Abra **Configurações** pelo menu do ícone ou com duplo clique.
6. Confira as impressoras detectadas e mantenha apenas as origens web necessárias na aba de segurança.
7. Copie o token de pareamento e informe-o na configuração de impressão do front-end. Ele é salvo no armazenamento local do navegador; não o inclua no código publicado, em repositórios ou em logs.

As configurações ficam em `%USERPROFILE%\.gabryelle-print-agent\config.json`. O arquivo é criado na primeira inicialização e atualizado de forma atômica.

## Compilar e testar

No terminal do IntelliJ, na pasta do projeto:

```powershell
mvn test
mvn package
```

O JAR executável com dependências será gerado em `target\gabryelle-print-agent.jar`. Para executar:

```powershell
java -jar target\gabryelle-print-agent.jar
```

## Gerar instalador/executável Windows

Com o JDK instalado, compile o projeto e instale o [WiX Toolset 3](https://wixtoolset.org/) para permitir que `jpackage` gere um instalador `.exe`. No PowerShell, na raiz do projeto:

```powershell
mvn package
New-Item -ItemType Directory -Force target\installer | Out-Null
jpackage --type exe --name "Gabryelle Print Agent" --app-version 1.0.0 `
  --input target --main-jar gabryelle-print-agent.jar `
  --main-class com.gabryelle.agent.Main --dest target\installer `
  --win-menu --win-shortcut
```

O instalador será criado em `target\installer`. Para distribuir para outros caixas, valide a assinatura/instalador e as políticas do Windows Defender antes da implantação.

## Protocolo WebSocket local

Endpoint: `ws://127.0.0.1:38182/ws`. O `Origin` do navegador deve estar cadastrado. Para o Live Server do VS Code em `http://127.0.0.1:5500`, inclua essa origem na aba **Segurança / domínios** do agente. Após receber `{"type":"ready"}`, envie o token:

```json
{"type":"authenticate","token":"TOKEN_COPIADO_DA_TELA"}
```

Após `{"type":"authenticated"}`, consulte as impressoras instaladas:

```json
{"type":"list-printers","requestId":"1"}
```

A resposta contém `type: "printers-result"`, `requestId` e a lista `printers`. Para imprimir, envie o nome exato de uma impressora dessa lista:

```json
{
  "type": "print",
  "printerName": "Nome da impressora no Windows",
  "imageBase64": "iVBORw0KGgo...",
  "cut": true
}
```

`printerName` seleciona uma impressora atualmente instalada no Windows. O agente aceita apenas recibos rasterizados enviados em `imageBase64` como PNG, para preservar o layout renderizado pelo navegador; comandos antigos de impressão em texto ou por setor são rejeitados. A resposta de impressão contém `type: "print-result"`, `requestId` e `success`, ou `type: "error"` e `message`. O trabalho precisa ser enviado a um driver Windows configurado para dados ESC/POS brutos. O corte usa o comando padrão `GS V 1`, não suportado por todos os modelos.

Para recibos com estilo, `imageBase64` contém o PNG monocromático do recibo renderizado pelo navegador. O agente o imprime centralizado como raster ESC/POS, limitado a 576 × 10.000 pixels e 320 KiB por imagem.


Exemplo mínimo no front-end:

```javascript
const socket = new WebSocket("ws://127.0.0.1:38182/ws");
socket.addEventListener("message", ({ data }) => {
  const response = JSON.parse(data);
  if (response.type === "ready") {
    socket.send(JSON.stringify({ type: "authenticate", token: tokenGuardadoLocalmente }));
  } else if (response.type === "authenticated") {
    socket.send(JSON.stringify({
      type: "print",
      requestId: "2",
      printerName: "Nome da impressora no Windows",
      imageBase64: "iVBORw0KGgo...",
      cut: true
    }));
  }
});
```

## Supabase e limites de segurança

`SupabaseConfig` contém a URL, a chave **publishable** e os identificadores `app_state` / `gp_state` / `data` informados. Esta versão não faz polling nem imprime diretamente a partir da tabela: o formato dos comandos e uma estratégia de deduplicação/acknowledgment não foram definidos. A chave publishable não substitui Row Level Security e não deve ser confundida com uma `service_role` key.

O serviço não aceita conexões de rede: ele se vincula apenas ao loopback, valida o host e a origem do handshake, exige um token aleatório de 256 bits e limita o tamanho/fila dos trabalhos. A lista de origens é uma defesa adicional, não substitui o token. Configure o token em armazenamento privado do front-end; qualquer pessoa com o token e acesso ao navegador autorizado pode solicitar impressões.
