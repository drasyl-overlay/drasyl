# Public Super Peers

Exposed drasyl nodes, configured as super peers and acting as rendezvous servers and relays, are required for discovery of other nodes.

We run some public super peers, so you don't have to.

| **Endpoint**  | **Used drasyl version**  |  
|---------------|--------------------------|
| `udp://sp-ham1.drasyl.org:22527?publicKey=AhHUC25XyNoXgPazJunJcsuPYsUimE+0OuxCBJ77W1ZP&networkId=1` | Latest stable [release](https://github.com/drasyl-overlay/drasyl/releases) | 
| `udp://sp-nue1.drasyl.org:22527?publicKey=AmHuiBcUG2JtNXOnZxWq0FPM7nQQ16ggwvyY+qTEhaWA&networkId=1` | Latest stable [release](https://github.com/drasyl-overlay/drasyl/releases) |
| `udp://staging.env.drasyl.org?publicKey=Awlq4wgKNpgppEhH1a8fZSvvP5kh6eG7rWSXC6vm08UC&networkId=-25421`  | Latest [nightly](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines?page=1&scope=all&ref=master&status=success)  |  

By default, all drasyl nodes are configured to use the super peers `sp-ham1.drasyl.org`
and `sp-nue1.drasyl.org`.

