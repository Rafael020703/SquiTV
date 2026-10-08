# Matriz de Eventos de Diagnóstico e Telemetria (Build Debug - SquiTV)

Esta documentação mapeia a matriz de eventos estruturados capturados pelo sistema central de diagnóstico em compilações `debug`.

| EVENTO | CATEGORIA | ORIGEM | QUANDO É DISPARADO | ID DE CORRELAÇÃO | DADOS REGISTRADOS |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APP_START** | `APP` | IptvApplication | Inicialização do aplicativo em `onCreate` | `sessionId`, `appLaunchId` | Metadata da build, versão do Android, modelo do aparelho |
| **ACTIVITY_CREATED** | `LIFECYCLE` | DebugLifecycleObserver | Evento `onCreate` da Activity | `sessionId` | Nome da Activity, status de recriação/configChange, Intent |
| **ACTIVITY_RESUMED** | `LIFECYCLE` | DebugLifecycleObserver | Evento `onResume` da Activity | `sessionId` | Nome da Activity atualizada |
| **ACTIVITY_PAUSED** | `LIFECYCLE` | DebugLifecycleObserver | Evento `onPause` da Activity | `sessionId` | Nome da Activity |
| **ACTIVITY_DESTROYED** | `LIFECYCLE` | DebugLifecycleObserver | Evento `onDestroy` da Activity | `sessionId` | `isFinishing`, `isChangingConfigurations` |
| **NAVIGATION_START** | `NAVIGATION` | AppController | Solicitação de navegação entre rotas | `actionId` | Rota de origem, rota de destino, parâmetros de popUpTo |
| **NAVIGATION_BACK** | `NAVIGATION` | AppController | Pressionamento de BACK ou chamada `goBack()` | `actionId` | Rota atual, eventId de navegação |
| **KEY_EVENT** | `DPAD` / `INPUT` | MainActivity / DebugInputTracker | Entrada física do controle/teclado em `dispatchKeyEvent` | `actionId` | Tecla acionada (DPAD_UP, ENTER, BACK, etc.), ação (KEY_DOWN/UP), repeatCount |
| **FOCUS_GAIN** / **FOCUS_LOSS** | `FOCUS` | DebugInputTracker | Alteração de foco em componente de tela | `actionId` | Nome/ID da View ou componente, tela atual, status do foco |
| **UI_CLICK** | `UI` | DebugInputTracker | Seleção/clique em card, botão ou canal | `actionId` | Elemento clicado, ID do canal (quando aplicável), tela |
| **CHANNEL_SWITCH_START** | `CHANNEL_SWITCH` | PlayerViewModel | Início do processo de troca/zapping de canal | `channelSwitchId` | Canal anterior, canal solicitado, nome de exibição, flags isZapping/isRecovery |
| **CHANNEL_SWITCH_STOP_OLD** | `CHANNEL_SWITCH` | PlayerViewModel / PlaybackManager | Encerramento do socket/player do canal anterior | `channelSwitchId` | ID do canal anterior liberado, tempo gasto na parada |
| **NETWORK_REQUEST_START** | `HTTP` / `STREAM` | DebugNetworkInterceptor | Início de requisição HTTP (API ou Stream ExoPlayer) | `operationId` | Método HTTP, Host, Path, URL mascarada |
| **NETWORK_REQUEST_END** | `HTTP` / `STREAM` | DebugNetworkInterceptor | Conclusão com sucesso de requisição HTTP | `operationId` | Status HTTP (ex: 200), tamanho da resposta, duração ms |
| **NETWORK_REQUEST_ERROR** | `HTTP` / `STREAM` | DebugNetworkInterceptor | Falha ou timeout de requisição HTTP | `operationId` | Nome da exceção, mensagem mascarada, duração ms |
| **PLAYER_CREATE** | `PLAYER` | PlaybackManager / DebugPlayerLogger | Instanciação e conexão do controller do player | `playerInstanceId` | ID incremental do Player |
| **PLAYER_PREPARE** | `PLAYER` | PlaybackManager | Invocação do comando `prepare()` do player | `playerInstanceId`, `channelSwitchId` | MediaItem associado |
| **PLAYER_STATE_CHANGED** | `PLAYER_STATE` | DebugPlayerLogger | Alteração no estado interno do ExoPlayer | `playerInstanceId`, `channelSwitchId` | Estado anterior, novo estado (IDLE, BUFFERING, READY, ENDED) |
| **PLAYER_IS_PLAYING_CHANGED** | `PLAYER_STATE` | DebugPlayerLogger | Alteração do estado de reprodução ativa | `playerInstanceId`, `channelSwitchId` | `isPlaying` (true/false) |
| **PLAYER_RENDERED_FIRST_FRAME** | `PLAYER` | DebugPlayerLogger | Exibição do primeiro frame de vídeo renderizado | `playerInstanceId`, `channelSwitchId` | Latência acumulada de zapping |
| **PLAYER_ERROR** | `PLAYER_ERROR` | DebugPlayerLogger | Erro retornado pelo ExoPlayer/Media3 | `playerInstanceId`, `channelSwitchId` | ErrorCode, ErrorName, causa raiz, stack trace |
| **PLAYER_RELEASE** | `PLAYER` | PlaybackManager / DebugPlayerLogger | Liberação dos recursos do player | `playerInstanceId` | Estado final |
| **SESSION_LOGIN_START** | `AUTH` | AuthManager | Início da tentativa de autenticação IPTV | `operationId` | BaseUrl mascarada |
| **SESSION_LOGIN_SUCCESS** | `AUTH` | AuthManager | Login autenticado com sucesso | `operationId` | Status da conta, conexões ativas/máximas |
| **SESSION_LOGIN_FAILURE** | `AUTH` | AuthManager | Recusa de credenciais pelo servidor | `operationId` | Motivo de recusa |
| **OPERATION_TIMEOUT** | `TIMER` | DebugLogger (Watchdog) | Operação que excedeu o tempo limite configurado | `operationId`, `channelSwitchId` | Nome da operação, limite de timeout, tempo transcorrido |
| **CALLBACK_AFTER_RELEASE** | `ANOMALY` | DebugPlayerLogger | Evento recebido após liberação do player | `playerInstanceId` | Nome do callback recebido tardiamente |
| **SNAPSHOT_TAKEN** | `PERFORMANCE` | DebugLogger | Foto instantânea do estado do aplicativo | `sessionId` | Rota, Activity, Canal, Estado do Player, Memória Usada, Conexões Ativas |
| **UNCAUGHT_EXCEPTION** | `EXCEPTION` | DebugExceptionHandler | Exceção não capturada no aplicativo | `sessionId` | Thread da falha, tipo da exceção, stack trace completo |
