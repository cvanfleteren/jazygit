{
  description = "jazygit, a terminal UI for git";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
      jazygit = pkgs:
        let jdk = pkgs.graalvmPackages.graalvm-ce;
        in pkgs.maven.buildMavenPackage {
          mvnJdk = jdk;
          pname = "jazygit";
          version = "0.1.0-SNAPSHOT";
          src = self;
          # Changes with every dependency in the poms; a build with the old one fails and prints the new one.
          mvnHash = "sha256-mBViB7S1rg7kWEQXXmf6fUK9Fy9QWIkhU7GBzENHTk0=";
          # The tests and checks run in CI; spotless would rewrite the sources it builds.
          mvnParameters = "-T1 -Pnative -DskipTests -Dspotless.skip";
          nativeBuildInputs = [ pkgs.makeWrapper ];
          installPhase = ''
            runHook preInstall
            install -Dm755 jazygit-app/target/jazygit $out/bin/jazygit
            # A git and gh already on the PATH come first.
            wrapProgram $out/bin/jazygit --suffix PATH : ${pkgs.lib.makeBinPath [ pkgs.gitMinimal pkgs.gh ]}
            runHook postInstall
          '';
          meta = {
            description = "A terminal UI for git";
            homepage = "https://github.com/cvanfleteren/jazygit";
            license = pkgs.lib.licenses.gpl3Only;
            mainProgram = "jazygit";
          };
        };
    in {
      packages = forAllSystems (pkgs: { default = jazygit pkgs; });

      overlays.default = final: prev: { jazygit = jazygit final; };

      devShells = forAllSystems (pkgs:
        let jdk = pkgs.graalvmPackages.graalvm-ce;
        in {
          default = pkgs.mkShell {
            packages = [
              jdk
              (pkgs.maven.override { jdk_headless = jdk; })
              # The same git as CI, so a local run meets the git the tests meet there.
              pkgs.git
              # For the acceptance tests in acceptance/.
              (pkgs.python3.withPackages (p: [ p.pexpect p.pyte p.pytest p.pytest-xdist ]))
            ];
            JAVA_HOME = jdk.home;
          };
        });
    };
}
