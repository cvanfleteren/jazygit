{
  description = "jazygit, a terminal UI for git";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  # Pinned to the nixpkgs commit that ships git 2.45.1, for the dev shell's git.
  inputs.nixpkgs-git.url = "github:NixOS/nixpkgs/4284c2b73c8bce4b46a6adf23e16d9e2ec8da4bb";

  outputs = { self, nixpkgs, nixpkgs-git, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
      # The version of the parent pom, the one the publish workflow sets.
      version = builtins.head (builtins.match
        ".*<artifactId>jazygit-parent</artifactId>[[:space:]]*<version>([^<]*)</version>.*"
        (builtins.readFile ./pom.xml));
      # Only what the build reads, so a change to the documentation or the acceptance tests doesn't rebuild it.
      src = with nixpkgs.lib.fileset; toSource {
        root = ./.;
        fileset = unions [
          ./pom.xml
          ./.mvn
          ./checkstyle.xml
          ./errorprone-ignorable-results.txt
          ./jazygit-test-support
          ./jazygit-json
          ./jazygit-syntax
          ./jazygit-process
          ./jazygit-git
          ./jazygit-gh
          ./jazygit-app
        ];
      };
      jazygit = pkgs:
        let jdk = pkgs.graalvmPackages.graalvm-ce;
        in pkgs.maven.buildMavenPackage {
          mvnJdk = jdk;
          pname = "jazygit";
          inherit version src;
          # Changes with every dependency in the poms; .github/workflows/nix.yml puts the new one in a pull request.
          mvnHash = "sha256-dz1x8yYND/SMLjSrKPxBzXvnj9lWJBopvXWKTM2ncvA=";
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
        let
          jdk = pkgs.graalvmPackages.graalvm-ce;
          git = nixpkgs-git.legacyPackages.${pkgs.stdenv.hostPlatform.system}.git;
        in {
          default = pkgs.mkShell {
            packages = [
              jdk
              (pkgs.maven.override { jdk_headless = jdk; })
              # The same git as CI, so a local run meets the git the tests meet there.
              git
              # For the acceptance tests in acceptance/.
              (pkgs.python3.withPackages (p: [ p.pexpect p.pyte p.pytest p.pytest-xdist ]))
            ];
            JAVA_HOME = jdk.home;
          };
        });
    };
}
