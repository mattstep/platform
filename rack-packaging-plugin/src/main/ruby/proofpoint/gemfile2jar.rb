#
# Copyright 2010 Proofpoint, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
require 'rubygems'
require 'bundler'
require 'rubygems/dependency_installer'

# This is required because Bundler memoizes its initial configuration,
# which is all fine and dandy for normal operation, but plays hell when
# you want to use it twice within a single ruby invocation for testing.
module Bundler
  class << self
    def reset
      @configured = nil
    end
  end
end

module Proofpoint
  module GemToJarPackager

    class Gemfile2Dir
      def install_gems_from_gemfile(target_dir, gemfile_name)
        gemfile = Pathname.new(gemfile_name).expand_path
        root = gemfile.dirname
        @lockfile = root.join("#{gemfile.basename}.lock")

        ENV['BUNDLE_GEMFILE'] = gemfile

        Bundler.settings[:path] = target_dir
        Bundler.settings[:disable_shared_gems] = 1

        begin
          Bundler.configure
          definition = Bundler::Definition.build(gemfile, @lockfile, nil)
          Bundler::Installer::install(root, definition, {})
        rescue Exception => e
          puts e.message
          return false
        end
        return true
      end
    end
  end
end
