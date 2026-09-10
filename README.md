# Pono

Open Source SMT Pick and Place Software

> **Pono 是 [OpenPnP](https://github.com/openpnp/openpnp) 2.6 的修改版**，依 GNU GPL v3 发布。
> 分叉自上游提交 `5bd404cfc7`（2026-02-27），当前版本 `2.6-pono.1`（2026-09）。
> 相对上游的全部改动见 [CHANGES.md](CHANGES.md) 与 git 历史。
> Java 包名 `org.openpnp` 与配置目录 `~/.openpnp2` 保持不变，`machine.xml` 与 OpenPnP 互相兼容。
>
> **Pono is a modified version of [OpenPnP](https://github.com/openpnp/openpnp) 2.6**, released under the GNU GPL v3.
> Forked from upstream commit `5bd404cfc7` (2026-02-27); current version `2.6-pono.1` (September 2026).
> Everything that differs from upstream is listed in [CHANGES.md](CHANGES.md) and in the git history.
> The Java package name `org.openpnp` and the configuration directory `~/.openpnp2` are unchanged,
> so `machine.xml` files are interchangeable with OpenPnP.

## About OpenPnP (upstream)

OpenPnP is a project to create the plans, prototype and software for a completely Open Source SMT
pick and place machine that anyone can afford. I believe that with the ubiquity of cheap, precise
motion control hardware, some ingenuity and plenty of Open Source software it should be possible
to build and own a fully functional SMT pick and place machine for under $1000.

## Project Status

OpenPnP is stable and in wide use. It is still under heavy development and new features are added continuously. See the [Downloads](http://openpnp.org/downloads) page to get started.

If you would like to keep up with our progress you can
[Watch this project on GitHub](http://github.com/openpnp/openpnp), check out
[our Twitter](http://twitter.com/openpnp), [join the discussion group](http://groups.google.com/group/openpnp),
or come chat with us on [Discord](https://discord.gg/EmsrFVx).

## Contributing

![Build Status](https://github.com/openpnp/openpnp/workflows/Build%20and%20Deploy%20OpenPnP/badge.svg)
[![Help Wanted](https://img.shields.io/github/issues-raw/openpnp/openpnp/help-wanted.svg?label=help-wanted&colorB=5319e7)](https://github.com/openpnp/openpnp/labels/help-wanted)
[![Bugs](https://img.shields.io/github/issues-raw/openpnp/openpnp/bug.svg?label=bugs&colorB=D9472F)](https://github.com/openpnp/openpnp/labels/bug)
[![Feature Requests](https://img.shields.io/github/issues-raw/openpnp/openpnp/feature-request.svg?label=feature-requests&colorB=bfd4f2)](https://github.com/openpnp/openpnp/labels/feature-request)
[![Enhancements](https://img.shields.io/github/issues-raw/openpnp/openpnp/enhancement.svg?label=enhancements&colorB=0052cc)](https://github.com/openpnp/openpnp/labels/enhancement)


Before starting work on a pull request, please read: https://github.com/openpnp/openpnp/wiki/Developers-Guide#contributing

Summary of guidelines:

* One pull request per issue.
* Describe the change.
* Follow the coding style.
* Include tests and documentation.
* Think of the big picture.

## Thanks

Many thanks to ej-technologies for providing a complimentary license of install4j. install4j
creates high quality, professional installers for Java applications.

More information at http://www.ej-technologies.com/products/install4j/overview.html.
