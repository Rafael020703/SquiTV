package rsv.squitv.core.domain.state

sealed interface AppState {
    /** App carregando configurações iniciais e verificando credenciais */
    data object Initializing : AppState
    
    /** Nenhuma credencial IPTV encontrada. Ir para Login. */
    data object LoginRequired : AppState
    
    /** Credenciais existem, mas o banco de dados está vazio. Ir para Sync. */
    data object SyncRequired : AppState
    
    /** Sincronização em progresso. */
    data object Synchronizing : AppState
    
    /** App pronto para uso. Ir para Dashboard. */
    data object Ready : AppState
    
    /** Falha crítica de inicialização. */
    data class Error(val message: String) : AppState
}
