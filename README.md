# BOXPLAY

Aplicativo para soundboard/player offline com 20 boxes compactos e independentes de audio.

## Android

A versao Android entrega um MVP funcional para teste em celular:

- 20 boxes em grade compacta com rolagem vertical;
- indicador visual de rolagem na lateral direita da lista;
- upload de arquivo de audio pelo seletor do Android;
- botao Salvar copiando o arquivo para o armazenamento interno privado do app;
- Play/Pause por box usando players independentes com Media3/ExoPlayer;
- Reiniciar por box voltando para 00:00;
- volume independente por box com botoes de ajuste;
- cadeado bloqueando envio, salvamento e volume, sem bloquear Play/Pause e Reiniciar;
- persistencia de nome, caminho interno, volume e cadeado via DataStore;
- botao superior para parar todos os audios.

APK de depuracao:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Windows desktop

A versao Windows fica em `desktop-windows` e usa Electron. Ela possui:

- 20 boxes prontos;
- lista com barra de rolagem nativa do Windows/Chromium;
- envio de audio por seletor de arquivo;
- salvamento local na pasta privada do BOXPLAY no Windows;
- Play/Pause, Reiniciar, volume, cadeado e Parar todos;
- persistencia em arquivo JSON dentro da pasta de dados do aplicativo.

Comandos da versao Windows:

```powershell
cd X:\GPT\boxplay\desktop-windows
npm install
npm test
npm run check
npm run dist
```

O instalador Windows sera gerado em:

```text
desktop-windows\dist
```

## Comandos Android

No PowerShell, dentro de `X:\GPT\boxplay`:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat :app:assembleDebug
```

## Proxima fase recomendada

Validar no aparelho ou computador real: salvar varios audios, tocar dois ou mais simultaneamente, pausar/continuar, reiniciar, bloquear, rolar ate os boxes finais e fechar/abrir o app para confirmar a persistencia.
