# Changelog

## [0.1.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.0.5...v0.1.0) (2023-08-24)


### Enhancements

* Add authentication DEV-2296 ([#15](https://github.com/dasch-swiss/dsp-ingest/issues/15)) ([436b403](https://github.com/dasch-swiss/dsp-ingest/commit/436b403ab52b4c70dbe3f7258df302e0e767cedf))
* Add import endpoint DEV-2107 ([#11](https://github.com/dasch-swiss/dsp-ingest/issues/11)) ([abf5496](https://github.com/dasch-swiss/dsp-ingest/commit/abf54963dbb243c237cd8bcbf7a20ce5c8dda42e))
* Add info endpoint ([#16](https://github.com/dasch-swiss/dsp-ingest/issues/16)) ([96de600](https://github.com/dasch-swiss/dsp-ingest/commit/96de60072209bf0c0f72e0addc1d1a35e0aa6f2e))
* Add topleft correction maintenance DEV-1650 ([#53](https://github.com/dasch-swiss/dsp-ingest/issues/53)) ([f37d543](https://github.com/dasch-swiss/dsp-ingest/commit/f37d543a17e42207f4eeac4882d91256d730e926))
* In create-originals maintenance action update AssetInfo, and create originalFilename using a provided mapping ([#52](https://github.com/dasch-swiss/dsp-ingest/issues/52)) ([df2507e](https://github.com/dasch-swiss/dsp-ingest/commit/df2507ead2c8c4f6a7f68ff389af4dfa83e6f56c))


### Bug Fixes

* Change api projects root path, align with dsp-api ([#36](https://github.com/dasch-swiss/dsp-ingest/issues/36)) ([2b1ed2a](https://github.com/dasch-swiss/dsp-ingest/commit/2b1ed2ab3924c1b042aafc0050ad5d3af771bfd4))
* Enable request streaming ([#41](https://github.com/dasch-swiss/dsp-ingest/issues/41)) ([32813dd](https://github.com/dasch-swiss/dsp-ingest/commit/32813dd0bbe97855a82bd5e63fb5358e77680c0c))


### Maintenance

* Add FilesystemCheck on startup ([#42](https://github.com/dasch-swiss/dsp-ingest/issues/42)) ([9023c7e](https://github.com/dasch-swiss/dsp-ingest/commit/9023c7e3b5a58fee4b45f53ba677c6758ac35d04))
* Add release-please github action ([#63](https://github.com/dasch-swiss/dsp-ingest/issues/63)) ([413f8e9](https://github.com/dasch-swiss/dsp-ingest/commit/413f8e9dd542860bb5647fcb2e97557c71e20329))
* add Scala Steward ([#14](https://github.com/dasch-swiss/dsp-ingest/issues/14)) ([4413324](https://github.com/dasch-swiss/dsp-ingest/commit/44133249a205a2b2dca501ebe7d468ecf01b6081))
* add SIPI to the Docker image ([#8](https://github.com/dasch-swiss/dsp-ingest/issues/8)) ([30f8ef0](https://github.com/dasch-swiss/dsp-ingest/commit/30f8ef072bcf01a020442b7cece2a1b40ea1ab76))
* add workflow that pushes to Docker Hub on merge with main branch ([#13](https://github.com/dasch-swiss/dsp-ingest/issues/13)) ([cb09068](https://github.com/dasch-swiss/dsp-ingest/commit/cb09068d059b5b0a36c27723bbf23eabdc763732))
* Align logging with dsp-api ([#17](https://github.com/dasch-swiss/dsp-ingest/issues/17)) ([2263f99](https://github.com/dasch-swiss/dsp-ingest/commit/2263f99ffe246ea2472957f1747ce1219481f1be))
* Minor improvements ([#18](https://github.com/dasch-swiss/dsp-ingest/issues/18)) ([29f3230](https://github.com/dasch-swiss/dsp-ingest/commit/29f323020fc5c826ae10adb8dddf0b5501c72404))
* publish-docker also on pushing tags ([dddca47](https://github.com/dasch-swiss/dsp-ingest/commit/dddca478069df726d9698f42084b66a5e3e1f31b))
* Recreate originals DEV-2451 ([#51](https://github.com/dasch-swiss/dsp-ingest/issues/51)) ([a096c94](https://github.com/dasch-swiss/dsp-ingest/commit/a096c9428a38d890621960e28df0aea8e7b05c67))
* remove IntelliJ IDEA files ([#5](https://github.com/dasch-swiss/dsp-ingest/issues/5)) ([2d728c4](https://github.com/dasch-swiss/dsp-ingest/commit/2d728c4358b31bba983afafab5c209305b98ea01))
* Trigger ci workflow when pushing tags ([#62](https://github.com/dasch-swiss/dsp-ingest/issues/62)) ([664815a](https://github.com/dasch-swiss/dsp-ingest/commit/664815ae694d1b0a558244b5ef2697cd1babf59d))


### Documentation

* setup mkdocs and add documentation using OpenAPI ([#26](https://github.com/dasch-swiss/dsp-ingest/issues/26)) ([46714a0](https://github.com/dasch-swiss/dsp-ingest/commit/46714a06f955c70ec8103c5540336d1e17e65390))