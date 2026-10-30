# Próxima versão — atualização do aplicativo e revisão direcionada

Data: 30/10/2026
Base: v1.18, versionCode 19, commit dcf9848.
Estado: alterações locais para a próxima versão. A numeração ainda é a da base.

## Alterações aplicadas

| Área | Problema encontrado | Implementação |
| --- | --- | --- |
| Aviso da dashboard | Modal carregado e fechamento em 5 segundos | Aviso compacto, duas ações principais, silenciar versão e contagem de **10 segundos**. O tempo pausa fora do primeiro plano e é preservado em recriações da interface. |
| Tela de atualização | Cartões grandes, textos em caixa alta e apresentação de Markdown técnico bruto | Componentes visuais comuns, versões instalada/disponível, tamanho e data, novidades expansíveis, ações de pelo menos 48 dp e foco para controle remoto. |
| Progresso | Verificação do APK apresentada como download em 100% | Estados distintos: consulta, disponível, download, verificação, permissão, arquivo pronto e instalador aberto. |
| Login | Aviso com estilo próprio e textos fixos | Aviso no mesmo padrão visual. Novos textos em português, inglês e espanhol; textos antigos sem consumidores removidos após análise do lint. |
| Consulta ao GitHub | Intervalo de 12 horas não impedia nenhuma chamada; falhas podiam resultar em “atualizado” | Consultas serializadas por Mutex; cache dos metadados completos por 12 horas durante a sessão; consulta nova ao iniciar outro processo; verificação manual ignora o cache. Erros são apresentados como erro e somente HTTP 404 habilita o repositório legado. |
| Silenciar versão | Ignorar a atualização exibia “você está atualizado” | Mantém a atualização visível nas configurações, identifica os avisos silenciados, oculta o aviso também no login e permite baixar depois. |
| Download | Cancelar alterava o estado, mas não interrompia a conexão nem a gravação; respostas HTTP podiam ficar abertas | Cancelamento real de Job e Call, fechamento da resposta/streams, limpeza em finally e exclusão dos arquivos temporários. Downloads são serializados. |
| Arquivo baixado | Cache aceitava qualquer arquivo não vazio quando não havia SHA-256 | Confere tamanho, SHA-256 quando publicado, pacote, versão anunciada, versionCode superior e assinatura compatível. A verificação do instalador Android continua sendo a decisão final. |
| Desempenho do progresso | Estado atualizado a cada bloco de 8 KB | Leitura em blocos de 64 KB e publicação de progresso no máximo a cada 200 ms durante a cópia. Não foi medido ganho de FPS, bateria ou memória. |
| Instalação | Retorno da permissão e falha ao iniciar o instalador sem uma transição clara | Só retoma automaticamente após a ação de liberar permissão; valida arquivo fora da thread principal; informa falha e permite repetir a etapa apropriada. Abrir o instalador não é tratado como instalação concluída. |
| Debug e release | APK do GitHub não corresponde ao pacote .debug instalado | Debug informa que é atualizado pelo Android Studio e não oferece instalação do APK de produção sobre si mesmo. |
| Notificação | Intenção inicial enviada antes de existir coletor de navegação podia ser perdida | Destino externo fica pendente até concluir inicialização/login/sincronização; consumo único e navegação singleTop. Respeita permissão e canal de notificações, sem marcar como notificada uma mensagem que não pôde ser publicada. |
| EPG da troca rápida | flow emitia a partir de corrotinas filhas em paralelo, violando as regras de emissão; a exceção era suprimida | channelFlow com send para emissão concorrente e propagação de cancelamento. A seleção da categoria e o loop de canais permanecem no fluxo existente. |
| Trabalho desnecessário | Configurações sem relação com reprodução recalculavam os parâmetros de PiP; captura de stack de navegação também na release | Observação apenas das opções de reprodução/PiP com distinctUntilChanged, coleta com lifecycle no login e na raiz, captura de stack e logs de rastreio restritos ao debug. |

## Arquivos principais

- `ui/updates/UpdateComponents.kt` e `UpdatePrompt.kt`: componentes, notas de versão e aviso de 10 segundos.
- `ui/settings/UpdatesScreen.kt` e `ui/viewmodel/settings/UpdateViewModel.kt`: apresentação e estados da atualização.
- `data/repository/UpdateRepository.kt`: consulta e cache de releases.
- `data/update/ApkDownloadManager.kt` e `PackageInstallerHelper.kt`: download, validação e instalação.
- `core/navigation/AppController.kt`, `MainNavGraph.kt` e `MainActivity.kt`: recebimento de intenções externas e redução de trabalho desnecessário.
- `domain/usecase/GetQuickSwitchUseCase.kt`: emissão concorrente do EPG.

Os caminhos de código acima têm como raiz `app/src/main/java/rsv/squitv/`.

## Verificação desta etapa

- Primeira compilação encontrou escapes de regex inválidos, corrigidos antes da recompilação.
- `:app:assembleDebug :app:compileReleaseKotlin`: BUILD SUCCESSFUL.
- Primeiro APK release com R8: gerado. O lint detectou leitura não observável de locale na formatação da data; corrigida para LocalLocale. A execução final completa está registrada abaixo.
- Nenhum teste unitário ou instrumentado novo foi adicionado ou executado nesta solicitação.
- A aprovação dos 218 testes registrada na publicação v1.18 é histórica e não valida estas alterações.
- Não houve execução em TV/celular/emulador nem inspeção visual em aparelho. Compilação e lint não comprovam comportamento real de foco, permissões ou instalação.
- O download pertence ao ViewModel; sua destruição cancela a tarefa. Não foi adicionado um serviço de download persistente após encerramento do processo.

### Resultado final

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintRelease --console=plain
```

**BUILD SUCCESSFUL em 3 min 4 s**, 100 tarefas (40 executadas, 60 atualizadas); Configuration Cache reutilizado.

| Verificação | Resultado |
| --- | --- |
| APK debug | Gerado, pacote rsv.squitv.debug, 36.895.943 bytes |
| APK release | Gerado com R8 e redução de recursos, pacote rsv.squitv, 7.271.453 bytes |
| Lint release | **0 erros / 0 fatais**, 344 avisos e 4 sugestões |
| Maiores grupos de avisos | 255 recursos sem uso, 26 LogNotTimber, 15 TypographyEllipsis, 10 GradleDependency |
| Integridade do diff | git diff --check sem problemas |
| Testes unitários/instrumentados | Não executados nesta solicitação |
| Execução em aparelho / inspeção visual | Não realizada nesta etapa |

Uma execução intermediária do lint ficou ocupada na mesma rotina de travessia PSI, conforme dois registros de threads. Ela foi interrompida com `gradlew --stop`. A repetição em um novo processo, com os arquivos finalizados, concluiu normalmente. Nenhuma regra de lint foi desativada.

Log final: `C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/validated-updater-next.log`.

Relatório HTML do lint: `app/build/reports/lint-results-release.html`.

## Próximas prioridades encontradas

| Prioridade | Achado | Próxima ação recomendada |
| --- | --- | --- |
| Alta | `watch_progress` usa somente streamId como chave, sem conta, perfil ou tipo. `UserRepository` consulta e grava por esse ID global. IDs repetidos podem associar o progresso ao conteúdo de outra conta/tipo e o histórico é compartilhado entre perfis. | Preparar migração sem perda de dados e escopo por conta/perfil/tipo; revisar histórico e continuar assistindo em conjunto. |
| Média | `DownloadEntity` também usa streamId como chave. Downloads de provedores ou tipos distintos podem compartilhar IDs. | Rever identidade dos downloads, referências do Media3 e preservação dos arquivos existentes antes de migrar. |
| Média | O fixture instrumentado constrói SettingsRepository com ContextWrapper, mas o armazenamento usa um delegate global preferencesDataStore. A troca de diretórios, sozinha, não demonstra isolamento do DataStore já criado no processo; o teardown também não encerra sua coroutine. | Injetar armazenamento e escopo próprios no fixture e garantir fechamento antes de apagar o diretório temporário. Trata-se de risco identificado por leitura, sem execução dos testes em aparelho. |
| Média | Fluxos reais de TV/celular e ganho global de desempenho ainda não foram medidos nesta etapa. | Automatizar cenários em emulador/dispositivo e medir inicialização, rolagem, alocações e troca de canal antes de definir otimizações maiores. |

## Referências técnicas consultadas

- [Kotlin: channelFlow e emissão concorrente](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/channel-flow.html).
- [Android: PackageManager e informações de assinatura](https://developer.android.com/reference/android/content/pm/PackageManager).

## Complemento posterior: navegação e próximas prioridades

Após a validação do atualizador acima, os botões visuais de voltar foram removidos por solicitação do usuário. Essa alteração foi compilada em debug e Kotlin release; os resultados de APK release/R8 e lint anteriores não incluem essa remoção. A revisão adicional e sua validação estão em [revisao-prioridades-2026-10-30.md](revisao-prioridades-2026-10-30.md).
