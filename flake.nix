{
  description = "jazygit dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in {
      devShells = forAllSystems (pkgs:
        let jdk = pkgs.graalvmPackages.graalvm-ce;
        in {
          default = pkgs.mkShell {
            packages = [ jdk (pkgs.maven.override { jdk_headless = jdk; }) ];
            JAVA_HOME = jdk.home;
          };
        });
    };
}
