package dr.ulysses.network

/**
 * Client that discovers other instances of the app on the network using UDP.
 */
expect class NetworkClient() {
    /**
     * Starts the discovery process to find servers on the local network.
     * @param onServersDiscovered Callback that will be called with the map of discovered servers (IP to port).
     */
    fun startDiscovery(onServersDiscovered: (Map<String, Int>) -> Unit)

    /**
     * Stops the discovery process.
     */
    fun stopDiscovery()

    /**
     * Connects to a custom server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onServersDiscovered Callback that will be called with the updated map of discovered servers.
     */
    fun connectToCustomServer(address: String, port: Int, onServersDiscovered: (Map<String, Int>) -> Unit)
}
