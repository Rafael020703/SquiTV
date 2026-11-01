# Simplificação do Squi TV — auditoria e compatibilidade

Escopo: EPG somente na reprodução ao vivo, agrupamento/qualidade compartilhados, ExoPlayer único, retirada de PIN e configurações sem efeito e tema escuro único. Sem alteração de versionCode/versionName e sem publicação.

As alterações de manutenção preexistentes em IptvApplication.kt, IptvDao.updateDownloadProgress e docs/manutencao-incremental.md foram preservadas. A atualização de AGP 9.4.0 para 9.5.0 feita pelo usuário durante a tarefa também foi preservada.

## Auditoria das configurações

A auditoria considera efeito no consumidor, não apenas persistência ou seleção no backup. A coluna consumidor identifica o caminho final, após callback e preferência. Ações sem preferência também aparecem porque são entradas visíveis de Configurações.

| Opção | Consumidor real | Efeito encontrado | Manter/remover | Justificativa |
|---|---|---|---|---|
| Conta, perfis e conexões | SettingsScreen → AccountScreen → AuthManager/ProfileManager/SettingsRepository | Gerencia a conta ativa e os perfis locais | Manter | Fluxo funcional; credenciais continuam criptografadas por SecurityHelper |
| Sair | SettingsScreen → MainViewModel.logout → AuthManager | Encerra a sessão | Manter | Ação efetiva, independente do PIN removido |
| Personalização da página inicial | ContentCustomizationScreen → hiddenSections → DashboardScreen | Filtra os cartões disponíveis | Manter | EPG saiu da lista; chaves EPG antigas são ignoradas |
| Categorias ocultas e ordem personalizada | ContentCustomizationScreen → hiddenCategoriesMap/categoryOrderMap → CatalogRepository.applyCategoryCustomization/CategoryViewModel | Oculta e ordena categorias da conta/tipo | Manter | Não depende de categorias bloqueadas |
| Categorias fixadas / mover para o topo | updateCategoryPinned e SettingsRepository.moveCategory | Mantém fixação e altera a ordem personalizada | Manter | Fixação não é PIN de segurança |
| Backup e sincronização local | SettingsBackupDialog/ViewModels → SettingsBackupRepository → DataStore/Room | Exporta/importa somente itens selecionados; aplica transferências aprovadas | Manter | Inclui agrupamento e qualidade, selecionados por padrão |
| Idioma | SettingsViewModel → SettingsRepository.language → ThemeManager/MainActivity | Atualiza os recursos de idioma | Manter | Tem consumidor na Activity |
| Tamanho da interface | SettingsViewModel → uiZoom → MeusCanaisTheme/LocalDensity | Aplica escala de densidade da interface | Manter | Altera efetivamente a apresentação |
| Agrupar versões do mesmo canal | SettingsViewModel ou PlayerViewModel → groupLiveChannels → CategoryViewModel/SearchViewModel/PlayerViewModel | Agrupa a lista e muda os passos de próximo/anterior | Manter (novo) | Mesma preferência nos dois locais; não reinicia a reprodução atual |
| Qualidade preferida | SettingsViewModel → preferredChannelQuality → LiveChannelGrouping.choose | Escolhe um endereço do provedor ao abrir/trocar de grupo | Manter (novo) | Não confunde endereço com faixa nem promete medir a conexão |
| Reprodução automática | SettingsViewModel → autoPlayEnabled → PlayerViewModel, evento STATE_ENDED | Inicia a contagem do próximo episódio | Manter | O consumidor é de episódios; descrição corrigida |
| Reprodução em segundo plano | SettingsViewModel → backgroundPlaybackEnabled → MainActivity/lifecycle de reprodução | Controla continuidade fora da interface | Manter | Independente de PiP |
| PiP ao minimizar | SettingsViewModel → pipAutoEnterEnabled → MainActivity/PictureInPictureParams | Controla entrada automática em PiP em dispositivos compatíveis | Manter | Ajuste independente de áudio em segundo plano |
| Atualizar catálogo / intervalo de sincronização | SyncManager/SyncDataUseCase e IptvApplication/WorkManager | Sincroniza LIVE/VOD/SERIES/EPG; agenda trabalho periódico | Manter | Não substitui nem apaga dados do usuário |
| Atualizações do aplicativo | UpdateViewModel/UpdateRepository | Consulta, baixa e instala atualização pelo fluxo existente | Manter | Aviso da dashboard de 10 segundos preservado |
| Teste de DNS | DnsTesterViewModel/DnsManager | Testa conectividade e permite a troca de DNS pelo fluxo existente | Manter | Tem chamadas de rede reais; não é o painel de diagnósticos removido |
| Limpar cache | MainViewModel.clearCache | Remove cache pelo caminho existente | Manter | Ação de manutenção funcional |
| Redefinir catálogo | SyncManager.clearCatalogData | Limpa o catálogo pelo fluxo explícito de confirmação | Manter | Não é uma migração automática nem limpeza de credenciais |
| Downloads somente no Wi-Fi | downloadWifiOnly → IptvApplication → DownloadManager.requirements | Exige rede não tarifada quando habilitado | Manter | Consumidor real; manutenção local preservada |
| Downloads inteligentes | smartDownloadsEnabled → SmartDownloadUseCase/LibraryViewModel | Prepara o próximo episódio para download | Manter | Fluxo funcional preservado |
| Idioma de áudio e legendas | selectTrack → SettingsRepository → PlayerViewModel.applyTrackPreferences → TrackSelectionParameters | Seleciona e reaplica faixas existentes | Manter | Usa as faixas reais do stream |
| Versão do canal (player ao vivo) | PlayerSettingsDialog → selectChannelVersion → playStream | Troca o endereço dentro do grupo e da lista de origem | Manter (novo) | Mostra ID/codec/resolução rotulada e seleção atual; não grava a preferência global |
| Faixa de vídeo | PlayerSettingsDialog → selectTrack/clearTrackOverride → Media3.TrackSelectionParameters | Seleciona faixa no endereço atual ou retorna ao padrão do stream | Manter | Não é a seleção SD/HD/FHD/4K do provedor |
| Proporção da imagem durante a reprodução | toggleResizeMode → resizeMode → PlayerView.resizeMode | Alterna ajuste, preenchimento e zoom no player | Manter | Diferente da preferência de proporção padrão sem consumidor |
| Áudio durante a reprodução | PlayerSettingsDialog → selectTrack → TrackSelectionParameters | Seleciona a faixa de áudio real | Manter | Limpar a seleção atua apenas no tipo correspondente |
| Legendas durante a reprodução | PlayerSettingsDialog → selectTrack/clearTrackOverride | Seleciona texto ou desabilita faixas de texto | Manter | Desabilitar texto não limpa mais o áudio; reativar uma faixa habilita texto novamente |
| Velocidade | PlayerSettingsDialog/PlayerSpeedDialog → setPlaybackSpeed → PlaybackManager | Altera a velocidade do Media3 | Manter | Consumidor real |
| Temporizador | PlayerSettingsDialog → setSleepTimer → PlayerViewModel.sleepTimerJob | Interrompe reprodução ao terminar a contagem | Manter | Consumidor real |
| Motor EXO/VLC/IJK | Antes: UI → preferência; PlayerModule sempre fornecia ExoPlayer | A escolha não mudava o motor | Remover | ExoPlayer/Media3 é o único caminho; valores antigos não são lidos nem importados |
| Estatísticas/diagnósticos do player | Antes: UI → showDiagnostics → backup; sem consumidor visível | Nenhum efeito da preferência | Remover | Painel FullInfoPanel sem chamadas também removido; logs e erros preservados |
| Estratégia de buffer | Antes: UI → bufferStrategy → backup; sem leitura por PlayerModule | Nenhuma mudança no LoadControl | Remover | Mantém os parâmetros de buffer efetivos existentes |
| Economia de dados | Antes: dataSaverMode → persistência/backup | Sem consumidor de reprodução ou rede | Remover | Não inventa nova redução de qualidade |
| Proporção padrão | Antes: defaultResizeMode → persistência/backup | Player iniciava em FIT e ignorava a preferência | Remover | Mantém FIT inicial e o ajuste funcional durante a reprodução |
| Modo compacto | Antes: compactMode → persistência/backup | Sem consumidor de layout | Remover | Layout responsivo existente permanece |
| Notificações detalhadas | Antes: detailedNotifications → persistência/backup | Sem consumidor em notificações | Remover | Não remove notificações funcionais |
| Tamanho das legendas | Antes: UI → subtitleSize → estado; não aplicado ao SubtitleView | Nenhuma alteração visual real | Remover | Seleção e desativação de legendas permanecem; tamanho efetivo padrão preservado |
| PIN, bloqueio do aplicativo e de categorias / ocultar bloqueadas | SecurityManager/AppState.Locked/diálogos/filtros | Restringia acesso usando PIN e isLocked | Remover | Fluxo de acesso livre mesmo com flags antigas; ocultação manual é preservada |
| Tema claro/escuro e OLED Black | useOledTheme → WhiteColorScheme ou BlackColorScheme | false ativava tema claro | Remover | Tema escuro único em Compose, XML, splash e barras; valor antigo ignorado |

## Agrupamento, qualidade e navegação

- LiveChannelGrouping centraliza o parser e a seleção em código de domínio, fora da recomposição.
- São aceitos marcadores completos no final do nome: SD, HD/720p, FHD/1080p e UHD/4K/2160p, inclusive com separadores, entre parênteses/colchetes/chaves e com índices sobrescritos do provedor, como HD¹/FHD². Prefixos explicitamente delimitados, como [HD] ou 4K |, também são reconhecidos. H264/H.264/AVC e H265/H.265/HEVC são codecs separados.
- A interpretação é conservadora: palavras internas, regiões, idiomas, números, +1 e edições permanecem no nome. Marcadores conflitantes não são apagados para forçar agrupamento. Rótulos sem padrão reconhecível permanecem individuais.
- A chave inclui conta/perfil, categoria, nome-base normalizado e família EPG. Sufixos de qualidade no ID EPG são normalizados. Um EPG ausente pode participar quando há uma única família conhecida, sem ambiguidade. IDs conhecidos conflitantes continuam separados; quando há várias famílias, o ID ausente permanece separado. Nomes vazios usam o ID individual.
- Cada grupo guarda todas as versões em uma lista, inclusive várias com a mesma resolução. O desempate é pelo ID real do provedor. Identidade visual e nome-base não dependem da qualidade preferida.
- Automático prioriza 4K, FHD, HD, SD e, por último, resolução não informada. Uma preferência explícita tenta a resolução exata, depois as inferiores em ordem decrescente, depois as superiores em ordem crescente e finalmente as não informadas. Codec desconhecido, FPS e resolução não são inventados.
- A preferência vale na seleção de endereço. Não existe promessa de adaptação à conexão; Media3 só pode adaptar faixas oferecidas dentro do próprio stream.
- PlaybackSourceRepository captura exatamente os itens visíveis e sua ordem. Favoritos, Recentes, Histórico e Busca não ganham versões vindas de outro conjunto. Conta/perfil são conferidos ao recuperar a origem.
- Agrupamento ligado percorre grupos; desligado percorre itens individuais. O loop é circular e uma lista de um grupo não reinicia o mesmo canal ao avançar.
- A escolha pontual de versão mantém a origem e não altera a preferência global. Alternar agrupamento altera a lista, sem chamar a reprodução novamente.
- IDs persistidos continuam sendo IDs de streams. O grupo tem uma chave visual separada; foco e último canal reconhecem qualquer versão integrante. A troca de qualidade não escreve nos favoritos.
- Uma ação explícita de desfavoritar o grupo remove os favoritos já existentes desse grupo; favoritar acrescenta um stream real apenas se ainda não há favorito no grupo.
- A origem capturada é mantida em memória durante a navegação. Se o processo for perdido ou a captura expirar, o player não amplia a lista: continua reproduzindo o item atual e exige reabrir a lista para recuperar a navegação exata.

## EPG e visual

A rota, tela, ViewModel, arte de cartão e atalhos da grade EPG foram removidos. Saíram também consultas de intervalo e coletores/agendamento de novos lembretes exclusivos da grade. As tabelas EPG/lembretes e workers existentes permanecem; trabalhos já agendados não são apagados. EpgRepository, GetPlayerEpgUseCase e sincronização do player continuam funcionando.

Os controles ao vivo mostram programa atual, horários convertidos de timestamps, progresso e até dois programas seguintes. O EPG do canal atual é reavaliado no limite do programa ou em até um minuto; o trabalho é cancelado ao trocar/parar a reprodução. O menu distingue Versão do canal de Faixa de vídeo e mostra apenas metadados conhecidos.

O tema é sempre escuro, com superfícies em níveis distintos. O menu do player usa os mesmos tokens e um painel lateral menor. Após o ajuste solicitado na continuação da tarefa, os corações têm 16 dp nos cards, com fundo de 24 dp e alvo de interação de 48 dp. O favorito do player voltou ao tamanho normal dos demais controles (24/32 dp no player geral e 26 dp nos controles ao vivo), sem reduzir os outros botões. Estado, descrição acessível e foco continuam presentes.

## Instalações antigas, backups e transferência local

- O estado salvo do NavController usa um novo namespace para não tentar restaurar destinos EPG ou argumentos antigos do player após a atualização. A pilha de navegação anterior é reiniciada uma vez; última categoria/canal, organização e dados persistidos permanecem.
- Não há migração destrutiva de Room nem exclusão de favoritos, progresso, contas, perfis ou credenciais. A coluna isLocked permanece como dado legado do schema, sem qualquer consumidor de bloqueio.
- Chaves DataStore aposentadas podem permanecer no armazenamento, mas não são lidas/aplicadas. PIN e flags antigas não deixam o usuário preso no acesso. Perfis antigos com campo pin são decodificados ignorando o campo.
- Novas exportações usam schema 3. O decoder aceita schemas 1, 2 e 3 e ignora campos desconhecidos, inclusive as preferências aposentadas. Valores EXO/VLC/IJK e useOledTheme=false não restauram motores ou temas removidos.
- Backups com apenas opções aposentadas são reconhecidos sem aplicar alterações e apresentam uma mensagem específica, em vez de serem tratados como arquivo inválido.
- O novo formato inclui groupLiveChannels e preferredChannelQuality nas opções de backup. Exportação e seleção de importação seguem o padrão de tudo disponível marcado; a sincronização local usa a mesma seleção.
- A importação continua associando organização/favoritos às contas e perfis conhecidos e mesclando favoritos, sem apagá-los para ajustar o formato. Credenciais/PIN não são exportados.
- Aplicativos antigos limitados a schemas 1/2 não aceitam schema 3. Para enviar os novos ajustes entre celular e TV, ambos precisam da versão com suporte ao schema 3. Esta versão pode receber backups antigos; o sentido inverso não é garantido.

## Login pela rede local e ícones

A página HTTP é autocontida, sem fontes/CDNs externos, com layout em duas colunas em telas largas e uma coluna em telas menores. Código, contagem regressiva, envio, erro, sucesso e sessão encerrada têm estados separados. Há mostrar/ocultar senha e cópia com alternativa para HTTP local sem Clipboard API. Foi retirado o deep link que tentava abrir o aplicativo no dispositivo errado.

O token só é consumido depois da autenticação e persistência bem-sucedidas. Uma senha incorreta permite nova tentativa na mesma sessão. Senhas mantêm espaços e caracteres especiais. O servidor lê Content-Length em bytes UTF-8, escapa JSON corretamente e usa cabeçalhos HTTP com CRLF. Envios concorrentes são serializados; limites de leitura/autenticação evitam solicitações indefinidas. Uma resposta admitida pode terminar mesmo se a navegação de sucesso fechar o listener. A identidade da sessão é revalidada antes de salvar credenciais. O endereço e provedor continuam os do fluxo existente.

Não há promessa de criptografia do tráfego HTTP local; a página orienta usar uma rede de confiança. Navegadores sem acesso à rede do aparelho, isolamento entre clientes do roteador e uma porta já ocupada continuam sendo limites de conectividade.

Os dez WebPs legados com o robô Android foram substituídos por PNGs da marca gerados deterministicamente em cinco densidades para cada variante. Android 8+ usa camadas adaptativas; o monocromático tem fundo transparente e o banner TV mantém proporção 16:9 com a marca legível. Manifest da Activity e do aplicativo usam os mesmos recursos. Celulares/tablets usam os recursos de launcher por API/densidade; Android TV/Fire TV têm ícone legado/adaptativo e banner. A aplicação de máscaras e o uso de artes da loja dependem de cada launcher e não foram certificados em aparelhos Fire TV nesta sessão.

Notificações de reprodução Media3, downloads e lembretes usam o mesmo pequeno ícone branco com transparência, sem um fundo colorido que se transforme em bloco ao ser tingido pelo sistema. A geração dos PNGs roda como tarefa de recursos do Gradle em debug e release; não depende de arquivos locais fora do projeto.

## Revisão de agrupamento, configurações e categorias

O parser anterior não reconhecia o formato HD¹, encontrado nos logs reais do aplicativo (PFC CLUBES HD¹). O índice sobrescrito agora é tratado como identificação de uma versão, mantendo seu nome completo e ID no seletor. PFC 2 permanece um canal distinto. Foram corrigidos também os casos de EPG ausente/IDs terminados em qualidade e o reagrupamento de itens já agrupados: expandir e reagrupar conserva todas as versões.

Próximo/anterior usa uma função compartilhada que encontra o grupo contendo o ID real atual, mesmo se ele não for a versão principal. O loop permanece circular, sem reiniciar um grupo único. A alteração de agrupamento reconstrói a navegação do player imediatamente, sem chamar a reprodução novamente. Com agrupamento desligado, a lista continua individual. CH+/CH− e teclas de mídia/página funcionam também com os controles visíveis; as setas direcionais respeitam os botões quando o painel está aberto.

Configurações usa uma única grade responsiva, sem seções temáticas ou rolagens horizontais por seção. A ordem alfabética segue os textos traduzidos e Collator da configuração de idioma atual.

ContentCategories e CategoryViewModel agora produzem as entradas e contagens a partir do conteúdo local observado pelo Room. Para filmes e séries há acesso direto a Todos, gêneros existentes e Sem gênero informado, além de Favoritos, Recentes e Continuar assistindo somente quando contêm itens. Não é mais necessário entrar em uma categoria do provedor e depois aplicar um gênero.

Os gêneros usam os metadados efetivamente disponíveis nos títulos, sem inferência pelo nome da categoria do servidor. Por isso, Ação não é oferecida sem um título com gênero correspondente. Conteúdo sem metadados não é perdido nem classificado por suposição. Esse conteúdo permanece em Todos/Sem gênero informado; obter metadados que o provedor não forneceu não faz parte desta revisão.

Na TV ao vivo, as categorias do provedor permanecem, filtradas pelo conteúdo e pela ocultação manual. A contagem respeita a preferência de agrupamento. Nas listas de filmes/séries, uma categoria antiga do servidor ou um gênero que ficou vazio redireciona para uma entrada válida; não altera favoritos ou credenciais. As preferências de ocultação e ordem preexistentes continuam armazenadas e a ocultação manual do provedor continua filtrando os títulos.

A barra lateral apresenta a contagem em um espaço próprio, mantendo-a visível mesmo com nomes longos. As entradas têm chaves estáveis e o foco inicial tem um único destino selecionado. Telas compactas apresentam as mesmas entradas em uma faixa horizontal. O índice de gêneros é refeito por mudanças do catálogo/metadados ou opções relevantes, fora da recomposição; alterações de favoritos/progresso reaproveitam o índice e atualizam as listas especiais. Não há consultas de rede por categoria nem buscas de versões em outras listas.

Os atalhos de gênero dos detalhes de filmes e séries usam os mesmos IDs canônicos do índice e mantêm o tipo do conteúdo. Rotas antigas com type=genre são convertidas ao entrar, sem misturar filmes e séries nem pesquisar gênero pelo nome do título.

Novos testes cobrem o ViewModel real com ciclos de desligar/religar agrupamento, fonte de reprodução preservada, gêneros que surgem/desaparecem, favoritos vazios, ocultação manual reversível e IDs de atalhos dos detalhes. Previews da grade alfabética e das categorias com contagens foram renderizados e inspecionados.

## Verificações

Rodada final em 01/11/2026, após os ajustes de login, ícones, favoritos e a revisão de agrupamento/categorias/configurações. Uma compilação limpa foi executada para recriar artefatos gerados do Kotlin; as tarefas finais foram executadas sequencialmente:

| Verificação | Resultado |
|---|---|
| git diff --check | Aprovado; sem erros de whitespace. Git informa normalização futura LF/CRLF em alguns arquivos |
| :app:testDebugUnitTest | Aprovado: 284 testes, 0 falhas, 0 ignorados |
| :app:assembleDebug | Aprovado |
| :app:assembleRelease | Aprovado, incluindo R8 e redução de recursos |
| :app:lintDebug --no-parallel --max-workers=1 | Aprovado: 0 erros, 314 avisos e 4 sugestões |
| :app:assembleDebugAndroidTest | Aprovado: APK de testes compilado, sem execução instrumentada em aparelho |
| JavaScript do login local | Sintaxe validada pelo compilador V8; isso não executa DOM ou layout |
| Previews Compose | Grade ampla/compacta, menu do player, galeria de ícones/banner, grade alfabética de configurações e categorias com contagens renderizados e inspecionados |

Os testes incluem agrupamento, fallback, contexto/loop de navegação, backups antigos e estado salvo de foco. Para login local há requisições HTTP reais em loopback com autenticação simulada: tentativa inválida seguida de válida na mesma sessão, senha Unicode com espaços, JSON com aspas/quebras de linha, Content-Length em bytes, tokens inválidos/expirados/usados e resposta final após fechar o listener. Os recursos de ícones são carregados por Robolectric nas APIs 24 e 28, verificando bitmaps legados, camadas adaptativas, transparência e proporção do banner.

Limites confirmados: transferência de schema 3 para aplicativos antigos pode ser recusada; a captura da lista de reprodução não sobrevive à perda do processo; login local continua usando HTTP e requer conectividade entre os aparelhos. Não houve exclusão de dados para contornar esses limites.

Uma compilação debug desta revisão foi implantada no Android conectado sem apagar dados. A conferência da interface parou no bloqueio de tela do Android. A reinstalação após o último ajuste dos atalhos não ocorreu porque o dispositivo deixou de estar disponível. Não houve tentativa de desbloquear o aparelho; a navegação e CH+/CH− não foram verificados em execução nesta sessão.

Não verificado diretamente nesta sessão: layout/interações da página em um navegador (nenhum navegador conectado estava disponível), autenticação em um provedor real, navegação/foco em TV física, máscaras de launchers Fire TV, notificações no sistema e transições PiP/segundo plano em aparelho. Os previews e a compilação não comprovam esses comportamentos. Esses pontos são limites de validação, não falhas confirmadas.

O relatório de lint ainda registra recursos sem uso, APIs obsoletas e recomendações de PiP/ícones, entre outros avisos; nenhum foi tratado como comprovação de falha em aparelho. A manutenção desses avisos fora do escopo permanece pendente. As versões 1.19/20, o pacote debug separado e a alteração de AGP do usuário permanecem. Nenhuma release foi publicada.

