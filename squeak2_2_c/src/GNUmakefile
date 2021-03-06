# -*- Makefile -*- for Unix Squeak!
#
# last edited: Wed Oct  7 13:37:35 1998 by piumarta (Ian Piumarta) on olrik

MAJOR=	2
MINOR=	2
VMNAME=	SqueakVM

# To build the bytecoded interpreter, use:
#
#	make interp
#
# (or just "make").  To build the dynamic translator, use:
#
#	make trans
#
# To specify extra compilation flags, in addition to the defaults,
# use:
#	make <target> CFLAGS="additional flags"
#
# To replace the default compilation flags with your own, use:
#
#	make <target> CCFLAGS="replacement flags"
#
# To replace the default "final link" flags with your own, use:
#
#	make <target> LDFLAGS="replacement flags"
#
# To disable the "gnuification" stage (for use with compilers other
# than gcc, or simply out of curiosity to see how much performance you
# lose), use:
#
#	make <target> GNU=
#
# (i.e. empty).  To check paths, sources, libraries, platform
# configuration, the effects of flags and command-line definitions, or
# simply to see if your "make" has any chance whatsoever of building
# this thing, use:
#
#	make showconf
#
# Notes:
#
# - The makefiles will try to do (Squeak->Unix) line-end conversion
#   from a generated {interp,translator}.c if they can find one in the
#   parent directory; otherwise they expect a file of the same name in
#   this directory (with its line-end convention already converted to
#   Unix).
#
# - The makefiles work best when this directory "src", and the
#   utilities directory "util", are subdirectories of the directory in
#   which you normally generate VMs from within Squeak.  They were
#   also designed with the intention that you create a dedicated build
#   directory (also a subdir of the VM generation directory) for your
#   platform, rather than mixing source and object files together.
#   (Typing "make mkdir" in the parent directory is by far the best way
#   get started.)
#
# - If the support code sources are not in ../src/ then the makefiles
#   will look for them in this directory, or in a directory that you
#   specify with SRC=".../" on the "make" command line.  (Note the
#   trailing "/".)
#
# - If the utility programs are not in ../util/ then the makefiles
#   will look for them in this directory, or a directory that you
#   specify with UTIL=".../" on the "make" command line.  (Note the
#   trailing "/".)
#
# - The following make variables can be overridden on the command line
#   if you choose to call your generated interpreter source, and/or
#   compiled executable, by non-default names:
#
#	VMGEN	- the name of the VM source file generated by Squeak
#	VMNAME	- the "base" name of the final compiled executable
#
#   If you object to the version and/or architecture strings that are
#   normally appended to VMNAME, try defining VERSION and/or VMARCH as
#   empty on the "make" command line.  You can also use an alternate
#   build directory by defining the VMBUILD variable.
#
# - Add configurations for other platforms to GNUmakefile.conf and
#   then send your *additions* to the Squeak mailing list.  (Please do
#   not post the entire file.  If you absolutely must make other
#   changes, then send them as a set of "context diffs".)
#
# - If you don't have GNUmake, go away and install it before
#   proceeding.  (Failure to do so will invalidate your [imaginary]
#   warranty).
#
# But most important of all: ENJOY!

export MAKEFLAGS=--no-print-directory

.PHONY:		interp trans links

VMGEN:=		interp.c

include 	GNUmakefile.conf

UDP:=		sqUserPrims
VER:=		sqUnixVersion

SOURCES:=	$(subst $(SRC),,$(wildcard $(SRC)sq*.c))
SOURCES:=	$(filter-out $(UDP).c $(VER).c,$(SOURCES))
HEADERS:=	$(subst $(SRC),,$(wildcard $(SRC)sq*.h))
OBJECTS:=	$(patsubst %.c,%.o,$(SOURCES))

ifeq ($(VMGEN),translator.c)
  SUBVERSION=j1
endif

ifneq ($(findstring -DHEADLESS,$(CFLAGS)),)
  HEADLESS=h
endif

VERSION:=	$(MAJOR).$(MINOR)$(SUBVERSION)$(HEADLESS)
VM:=		$(VMNAME)

ifneq (,$(VERSION))
  VM:=	$(VM)-$(VERSION)
endif

ifneq (,$(VMARCH))
  VM:=	$(VM)-$(VMARCH)
endif

include 	GNUmakefile.rules

# targets

interp:
		$(MAKE) VMGEN=interp.c

trans:
		$(MAKE) VMGEN=translator.c VMNAME=Jitter

# this is for the benefit of the parent directory's makefile, and
# should be disregarded.

links:
		@/bin/rm -f GNUmakefile*
		@-ln -s ../src/GNUmakefile* .
		@/bin/rm -f sqUnixVersion.c *~
